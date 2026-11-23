package com.uel.homehelp.provider;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.common.web.ResourceNotFoundException;

/** Where a provider is willing to work. */
@Service
class ProviderServiceAreaService {

	static final int MAX_RADIUS_KM = 50;

	private final ProviderProfileRepository profiles;

	ProviderServiceAreaService(ProviderProfileRepository profiles) {
		this.profiles = profiles;
	}

	/** @throws ResourceNotFoundException if the provider hasn't set an area yet */
	@Transactional(readOnly = true)
	ServiceAreaResponse get(UUID providerId) {
		ProviderProfile profile = profile(providerId);
		if (!profile.hasServiceArea()) {
			throw new ResourceNotFoundException("Service area");
		}
		return ServiceAreaResponse.from(profile);
	}

	@Transactional
	ServiceAreaResponse set(UUID providerId, ServiceAreaRequest request) {
		ProviderProfile profile = profile(providerId);
		profile.setServiceArea(request.latitude(), request.longitude(), request.radiusKm());
		return ServiceAreaResponse.from(this.profiles.saveAndFlush(profile));
	}

	private ProviderProfile profile(UUID providerId) {
		return this.profiles.findById(providerId)
			.orElseThrow(() -> new ResourceNotFoundException("Provider profile"));
	}

}
