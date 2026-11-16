package com.uel.homehelp.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a user. Never includes the password hash.
 */
public record UserResponse(UUID id, String email, String fullName, String phone, Role role, Instant createdAt) {

	static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole(),
				user.getCreatedAt());
	}

}
