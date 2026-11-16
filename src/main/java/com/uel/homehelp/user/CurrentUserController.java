package com.uel.homehelp.user;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
class CurrentUserController {

	private final UserRepository users;

	CurrentUserController(UserRepository users) {
		this.users = users;
	}

	/** The logged-in user, identified by the access token's {@code sub} claim. */
	@GetMapping("/me")
	@Transactional(readOnly = true)
	UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return this.users.findById(UUID.fromString(jwt.getSubject()))
			.map(UserResponse::from)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User no longer exists"));
	}

}
