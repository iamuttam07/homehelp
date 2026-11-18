package com.uel.homehelp.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/users/me}. Replaces both fields: omitting {@code phone}
 * removes it. Email, password, and role are changed through other flows.
 */
record UpdateUserRequest(

		@NotBlank @Size(max = 100)
		String fullName,

		@Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be in international format, e.g. +447700900123")
		String phone) {
}
