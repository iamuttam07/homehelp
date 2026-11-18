package com.uel.homehelp.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Register, log in, refresh tokens, log out")
@SecurityRequirements
class AuthController {

	private final AuthService authService;

	AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	@Operation(summary = "Log in", description = "Returns a 15-minute access token and a single-use refresh token.")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return this.authService.login(request);
	}

	@PostMapping("/refresh")
	@Operation(summary = "Get new tokens", description = "Uses up the refresh token and returns a new access and refresh token. Reusing an old refresh token logs the user out everywhere.")
	TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return this.authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@Operation(summary = "Log out", description = "Revokes the refresh token. Always returns 204.")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@Valid @RequestBody RefreshTokenRequest request) {
		this.authService.logout(request.refreshToken());
	}

}
