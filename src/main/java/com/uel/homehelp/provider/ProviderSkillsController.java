package com.uel.homehelp.provider;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;

/** The logged-in provider's skills. {@code /api/provider/**} is PROVIDER-only (see SecurityConfig). */
@RestController
@RequestMapping("/api/provider/skills")
@Tag(name = "Provider onboarding", description = "Skills, service area, and availability (providers only)")
class ProviderSkillsController {

	private final ProviderSkillsService skills;

	ProviderSkillsController(ProviderSkillsService skills) {
		this.skills = skills;
	}

	@GetMapping
	@Operation(summary = "List my skills", description = "The catalog services I offer, in catalog order.")
	List<SkillResponse> get(@CurrentUserId UUID userId) {
		return this.skills.get(userId);
	}

	@PutMapping
	@Operation(summary = "Replace my skills",
			description = "Sends the full list of service slugs (up to 20). Unknown or inactive slugs are rejected.")
	List<SkillResponse> replace(@CurrentUserId UUID userId, @Valid @RequestBody SkillsRequest request) {
		return this.skills.replace(userId, request);
	}

}
