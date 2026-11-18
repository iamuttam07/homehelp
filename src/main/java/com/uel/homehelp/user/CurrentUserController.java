package com.uel.homehelp.user;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Current user", description = "The logged-in user's account")
class CurrentUserController {

	private final UserService userService;

	CurrentUserController(UserService userService) {
		this.userService = userService;
	}

	/** The logged-in user, identified by the access token. */
	@GetMapping
	@Operation(summary = "Get my account")
	UserResponse me(@CurrentUserId UUID userId) {
		return UserResponse.from(this.userService.get(userId));
	}

	/** Updates the logged-in user's name and phone. */
	@PutMapping
	@Operation(summary = "Update my name and phone", description = "Omitting phone removes it.")
	UserResponse update(@CurrentUserId UUID userId, @Valid @RequestBody UpdateUserRequest request) {
		return UserResponse.from(this.userService.update(userId, request));
	}

}
