package com.uel.homehelp.provider;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.common.web.PageResponse;
import com.uel.homehelp.common.web.ResourceNotFoundException;
import com.uel.homehelp.provider.ProviderReviewDtos.ProviderDetail;
import com.uel.homehelp.provider.ProviderReviewDtos.ProviderSummary;
import com.uel.homehelp.user.User;
import com.uel.homehelp.user.UserRepository;

/** The admin's side of onboarding: review submitted providers and approve or reject them. */
@Service
class ProviderReviewService {

	/** Oldest submissions first, so the queue is handled in order. */
	private static final Sort QUEUE_ORDER = Sort.by(Sort.Order.asc("submittedAt").nullsLast(), Sort.Order.asc("userId"));

	private final ProviderProfileRepository profiles;

	private final UserRepository users;

	private final ProviderSkillsService skills;

	private final ProviderAvailabilityService availability;

	private final ProviderOnboardingService onboarding;

	private final Clock clock;

	ProviderReviewService(ProviderProfileRepository profiles, UserRepository users, ProviderSkillsService skills,
			ProviderAvailabilityService availability, ProviderOnboardingService onboarding, Clock clock) {
		this.profiles = profiles;
		this.users = users;
		this.skills = skills;
		this.availability = availability;
		this.onboarding = onboarding;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	PageResponse<ProviderSummary> list(OnboardingStatus status, int page, int size) {
		Page<ProviderProfile> result = this.profiles.findByStatus(status, PageRequest.of(page, size, QUEUE_ORDER));
		Map<UUID, User> usersById = this.users.findAllById(result.map(ProviderProfile::getUserId).getContent())
			.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));
		return PageResponse.from(result, (profile) -> ProviderSummary.from(profile, usersById.get(profile.getUserId())));
	}

	@Transactional(readOnly = true)
	ProviderDetail get(UUID providerId) {
		return detail(profile(providerId));
	}

	@Transactional
	ProviderDetail approve(UUID adminId, UUID providerId) {
		ProviderProfile profile = lockedProfile(providerId);
		profile.approve(adminId, this.clock.instant());
		return detail(this.profiles.saveAndFlush(profile));
	}

	@Transactional
	ProviderDetail reject(UUID adminId, UUID providerId, String reason) {
		ProviderProfile profile = lockedProfile(providerId);
		profile.reject(adminId, reason.strip(), this.clock.instant());
		return detail(this.profiles.saveAndFlush(profile));
	}

	private ProviderDetail detail(ProviderProfile profile) {
		User user = this.users.findById(profile.getUserId()).orElse(null);
		return new ProviderDetail(profile.getUserId(), (user != null) ? user.getFullName() : null,
				(user != null) ? user.getEmail() : null, (user != null) ? user.getPhone() : null, profile.getStatus(),
				profile.getSubmittedAt(), profile.getReviewedAt(), profile.getRejectionReason(), profile.getBio(),
				profile.getYearsOfExperience(), this.onboarding.checklist(profile),
				this.skills.get(profile.getUserId()),
				profile.hasServiceArea() ? ServiceAreaResponse.from(profile) : null,
				this.availability.get(profile.getUserId()));
	}

	private ProviderProfile profile(UUID providerId) {
		return this.profiles.findById(providerId).orElseThrow(() -> new ResourceNotFoundException("Provider"));
	}

	/** Locks the row so two admins can't review the same provider at once. */
	private ProviderProfile lockedProfile(UUID providerId) {
		return this.profiles.findByIdForUpdate(providerId).orElseThrow(() -> new ResourceNotFoundException("Provider"));
	}

}
