package com.uel.homehelp.provider;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/provider/skills}. Replaces all skills; an empty list
 * removes them. Duplicates are ignored.
 *
 * @param serviceSlugs catalog service slugs, e.g. {@code ["leak-repair", "boiler-service"]}
 */
record SkillsRequest(
		@NotNull @Size(max = ProviderSkillsService.MAX_SKILLS) List<@NotBlank @Size(max = 80) String> serviceSlugs) {
}
