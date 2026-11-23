package com.uel.homehelp.common.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error raised while handling a request into an RFC 9457 problem
 * detail ({@code application/problem+json}).
 *
 * <ul>
 * <li>{@link ApiException}s and Spring MVC's own errors (404, 405, 415, ...) are
 * handled by the {@link ResponseEntityExceptionHandler} base class.</li>
 * <li>Validation errors list every invalid field.</li>
 * <li>Unexpected errors return a generic 500 with an {@code errorId} that is also
 * logged, so a report can be matched to the log without leaking internals.</li>
 * </ul>
 * 401 and 403 from Spring Security are rendered by {@code ProblemDetailsSecurityHandler}.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = new ArrayList<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.add(fieldError(error.getField(), error.getDefaultMessage()));
		}
		ex.getBindingResult()
			.getGlobalErrors()
			.forEach((error) -> errors.add(fieldError(error.getObjectName(), error.getDefaultMessage())));
		errors.sort((a, b) -> (a.get("field") + a.get("message")).compareTo(b.get("field") + b.get("message")));

		ProblemDetail problem = ApiException.problem(HttpStatus.BAD_REQUEST, "validation-failed",
				"Validation failed", "One or more fields are invalid.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/** Constraint violations on query or path parameters, e.g. {@code size=500}. */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = new ArrayList<>();
		boolean bodyErrors = false;
		for (var result : ex.getParameterValidationResults()) {
			RequestParam param = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
			String name = (param != null && !param.name().isEmpty()) ? param.name()
					: result.getMethodParameter().getParameterName();
			for (var error : result.getResolvableErrors()) {
				// A @Valid request body validated together with constrained parameters reports
				// its own field errors here; use the field name, not the parameter name.
				if (error instanceof FieldError fieldError) {
					bodyErrors = true;
					errors.add(fieldError(fieldError.getField(), fieldError.getDefaultMessage()));
				}
				else if (error instanceof ObjectError objectError) {
					bodyErrors = true;
					errors.add(fieldError(objectError.getObjectName(), objectError.getDefaultMessage()));
				}
				else {
					errors.add(fieldError(name, error.getDefaultMessage()));
				}
			}
		}
		errors.sort((a, b) -> (a.get("field") + a.get("message")).compareTo(b.get("field") + b.get("message")));

		ProblemDetail problem = ApiException.problem(HttpStatus.BAD_REQUEST, "validation-failed",
				"Validation failed",
				bodyErrors ? "One or more fields are invalid." : "One or more parameters are invalid.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// The parser's message can echo internal class names, so it isn't returned.
		ProblemDetail problem = ApiException.problem(HttpStatus.BAD_REQUEST, "malformed-request",
				"Malformed request body", "The request body is missing or is not valid JSON.");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = (ex instanceof MethodArgumentTypeMismatchException mismatch) ? mismatch.getName()
				: ex.getPropertyName();
		ProblemDetail problem = ApiException.problem(HttpStatus.BAD_REQUEST, "invalid-parameter",
				"Invalid parameter", "Parameter '%s' has an invalid value.".formatted(name));
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/** Two requests changed the same row at once (see the {@code version} columns). */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
		logger.info("Concurrent update rejected: {}", ex.getMessage());
		return ApiException.problem(HttpStatus.CONFLICT, "concurrent-update", "Concurrent update",
				"This item was changed by another request. Reload it and try again.");
	}

	/** A database constraint caught something the code didn't check first. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		logger.warn("Data integrity violation", ex);
		return ApiException.problem(HttpStatus.CONFLICT, "data-conflict", "Data conflict",
				"The request conflicts with existing data.");
	}

	/**
	 * Lets Spring Security turn these into 401/403. Without this, {@code @PreAuthorize}
	 * failures would be caught by the catch-all below and become 500s.
	 */
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	void rethrowSecurityException(RuntimeException ex) {
		throw ex;
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		String errorId = UUID.randomUUID().toString();
		logger.error("Unexpected error [errorId={}]", errorId, ex);
		ProblemDetail problem = ApiException.problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
				"Internal server error", "Something went wrong on our side. Please try again later.");
		problem.setProperty("errorId", errorId);
		return problem;
	}

	private static Map<String, String> fieldError(String field, String message) {
		Map<String, String> error = new LinkedHashMap<>();
		error.put("field", field);
		error.put("message", message);
		return error;
	}

}
