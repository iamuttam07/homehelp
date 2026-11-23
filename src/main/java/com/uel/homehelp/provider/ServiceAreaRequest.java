package com.uel.homehelp.provider;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/provider/service-area}.
 *
 * @param latitude base point, WGS 84 degrees (e.g. the provider's home or workshop)
 * @param longitude base point, WGS 84 degrees
 * @param radiusKm how far from the base point the provider travels, 1 to 50 km
 */
record ServiceAreaRequest(

		@NotNull @DecimalMin("-90") @DecimalMax("90")
		Double latitude,

		@NotNull @DecimalMin("-180") @DecimalMax("180")
		Double longitude,

		@NotNull @Min(1) @Max(ProviderServiceAreaService.MAX_RADIUS_KM)
		Integer radiusKm) {
}
