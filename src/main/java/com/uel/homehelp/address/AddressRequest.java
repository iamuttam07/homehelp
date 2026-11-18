package com.uel.homehelp.address;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/users/me/addresses} and {@code PUT /api/users/me/addresses/{id}}.
 *
 * @param label optional name such as "Home" or "Mum's flat"
 * @param countryCode ISO 3166-1 alpha-2, e.g. {@code GB}
 * @param latitude WGS 84 degrees, -90 to 90 (from the frontend's map or geocoder)
 * @param longitude WGS 84 degrees, -180 to 180
 */
record AddressRequest(

		@Size(max = 50)
		String label,

		@NotBlank @Size(max = 200)
		String line1,

		@Size(max = 200)
		String line2,

		@NotBlank @Size(max = 100)
		String city,

		@NotBlank @Size(max = 20)
		String postcode,

		@NotBlank @Pattern(regexp = "^\\s*[A-Za-z]{2}\\s*$", message = "must be a 2-letter country code, e.g. GB")
		String countryCode,

		@NotNull @DecimalMin("-90") @DecimalMax("90")
		Double latitude,

		@NotNull @DecimalMin("-180") @DecimalMax("180")
		Double longitude) {
}
