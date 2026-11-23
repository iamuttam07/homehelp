package com.uel.homehelp.provider;

/** A provider's service area: everything within {@code radiusKm} of the base point. */
record ServiceAreaResponse(double latitude, double longitude, int radiusKm) {

	static ServiceAreaResponse from(ProviderProfile profile) {
		return new ServiceAreaResponse(profile.getBaseLatitude(), profile.getBaseLongitude(),
				profile.getServiceRadiusKm());
	}

}
