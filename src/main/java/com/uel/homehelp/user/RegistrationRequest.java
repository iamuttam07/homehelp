package com.uel.homehelp.user;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}.
 *
 * @param email login email; stored trimmed and lower-cased
 * @param password 8 to 72 characters (BCrypt only uses the first 72 bytes)
 * @param fullName display name
 * @param phone optional, in E.164 format such as {@code +447700900123}
 * @param role {@code CUSTOMER} or {@code PROVIDER}; admins are not self-registered
 */
public record RegistrationRequest(

		@NotBlank @Email @Size(max = 254)
		String email,

		@NotBlank @Size(min = 8, max = 72)
		String password,

		@NotBlank @Size(max = 100)
		String fullName,

		@Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be in international format, e.g. +447700900123")
		String phone,

		@NotNull @Pattern(regexp = "CUSTOMER|PROVIDER", message = "must be CUSTOMER or PROVIDER")
		String role) {

	/** BCrypt silently ignores (and Spring Security rejects) bytes past 72. */
	@AssertTrue(message = "password must be at most 72 bytes")
	public boolean isPasswordWithinHashLimit() {
		return this.password == null || this.password.getBytes(StandardCharsets.UTF_8).length <= 72;
	}

}
