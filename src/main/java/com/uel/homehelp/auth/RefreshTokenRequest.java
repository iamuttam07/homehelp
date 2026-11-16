package com.uel.homehelp.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/refresh} and {@code POST /api/auth/logout}. */
record RefreshTokenRequest(@NotBlank @Size(max = 128) String refreshToken) {
}
