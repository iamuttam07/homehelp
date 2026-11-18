package com.uel.homehelp.provider;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.common.Strings;
import com.uel.homehelp.common.web.ResourceNotFoundException;
import com.uel.homehelp.user.Role;
import com.uel.homehelp.user.UserRegisteredEvent;

@Service
class ProviderProfileService {

	private final ProviderProfileRepository profiles;

	ProviderProfileService(ProviderProfileRepository profiles) {
		this.profiles = profiles;
	}

	/** Every new provider starts with an empty profile, in the same transaction as registration. */
	@EventListener
	void onUserRegistered(UserRegisteredEvent event) {
		if (event.role() == Role.PROVIDER) {
			this.profiles.save(new ProviderProfile(event.userId()));
		}
	}

	@Transactional(readOnly = true)
	ProviderProfile get(UUID userId) {
		return this.profiles.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("Provider profile"));
	}

	@Transactional
	ProviderProfile update(UUID userId, ProviderProfileRequest request) {
		ProviderProfile profile = get(userId);
		profile.update(Strings.trimToNull(request.bio()), request.yearsOfExperience());
		return this.profiles.saveAndFlush(profile);
	}

}
