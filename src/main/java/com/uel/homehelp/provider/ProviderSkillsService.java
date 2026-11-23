package com.uel.homehelp.provider;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.catalog.HomeService;
import com.uel.homehelp.catalog.HomeServiceRepository;
import com.uel.homehelp.common.web.ResourceNotFoundException;

/** Which catalog services a provider offers. */
@Service
class ProviderSkillsService {

	static final int MAX_SKILLS = 20;

	/** Same order as the catalog: by category, then service name. */
	private static final Comparator<HomeService> CATALOG_ORDER = Comparator
		.comparingInt((HomeService s) -> s.getCategory().getSortOrder())
		.thenComparing(HomeService::getName);

	private final ProviderProfileRepository profiles;

	private final HomeServiceRepository services;

	ProviderSkillsService(ProviderProfileRepository profiles, HomeServiceRepository services) {
		this.profiles = profiles;
		this.services = services;
	}

	/** Skills that are still bookable; deactivated services are left out. */
	@Transactional(readOnly = true)
	List<SkillResponse> get(UUID providerId) {
		return profile(providerId).getServices()
			.stream()
			.filter((s) -> s.isActive() && s.getCategory().isActive())
			.sorted(CATALOG_ORDER)
			.map(SkillResponse::from)
			.toList();
	}

	/**
	 * Replaces all of the provider's skills.
	 * @throws UnknownServicesException if any slug isn't an active catalog service
	 */
	@Transactional
	List<SkillResponse> replace(UUID providerId, SkillsRequest request) {
		Set<String> slugs = new LinkedHashSet<>();
		request.serviceSlugs().forEach((slug) -> slugs.add(slug.strip().toLowerCase(Locale.ROOT)));

		List<HomeService> found = slugs.isEmpty() ? List.of()
				: this.services.findBySlugInAndActiveTrueAndCategoryActiveTrue(slugs);
		if (found.size() != slugs.size()) {
			Set<String> foundSlugs = new LinkedHashSet<>();
			found.forEach((s) -> foundSlugs.add(s.getSlug()));
			throw new UnknownServicesException(slugs.stream().filter((s) -> !foundSlugs.contains(s)).toList());
		}

		ProviderProfile profile = profile(providerId);
		profile.replaceServices(found);
		this.profiles.saveAndFlush(profile);
		return found.stream().sorted(CATALOG_ORDER).map(SkillResponse::from).toList();
	}

	private ProviderProfile profile(UUID providerId) {
		return this.profiles.findById(providerId)
			.orElseThrow(() -> new ResourceNotFoundException("Provider profile"));
	}

}
