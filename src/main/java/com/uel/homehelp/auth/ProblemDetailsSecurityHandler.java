package com.uel.homehelp.auth;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.uel.homehelp.common.web.ApiException;

/**
 * Writes 401 and 403 responses from Spring Security as problem details, so clients
 * get the same error format everywhere. The standard bearer-token handlers still run
 * first and set the {@code WWW-Authenticate} header.
 */
@Component
class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

	private final AccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();

	private final JsonMapper jsonMapper;

	ProblemDetailsSecurityHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** No token, or an invalid/expired one: 401. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException, ServletException {
		this.bearerEntryPoint.commence(request, response, authException);
		boolean badToken = authException instanceof InvalidBearerTokenException;
		write(request, response, HttpStatus.UNAUTHORIZED, badToken ? "invalid-token" : "unauthorized",
				badToken ? "Invalid access token" : "Unauthorized",
				badToken ? "The access token is invalid or has expired."
						: "You need to log in to access this resource.");
	}

	/** Valid token, but the user's role isn't allowed: 403. */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException, ServletException {
		this.bearerAccessDenied.handle(request, response, accessDeniedException);
		write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
				"You don't have permission to access this resource.");
	}

	private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String type,
			String title, String detail) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", ApiException.TYPE_PREFIX + type);
		body.put("title", title);
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("instance", request.getRequestURI());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		this.jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
