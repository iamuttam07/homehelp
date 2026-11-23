package com.uel.homehelp.provider;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;
import com.uel.homehelp.common.web.PageResponse;
import com.uel.homehelp.provider.ProviderReviewDtos.ProviderDetail;
import com.uel.homehelp.provider.ProviderReviewDtos.ProviderSummary;
import com.uel.homehelp.provider.ProviderReviewDtos.RejectRequest;

/** Admin review of providers. {@code /api/admin/**} is ADMIN-only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/providers")
@Tag(name = "Admin: providers", description = "Review and approve providers (admins only)")
class ProviderReviewController {

	private final ProviderReviewService reviews;

	ProviderReviewController(ProviderReviewService reviews) {
		this.reviews = reviews;
	}

	@GetMapping
	@Operation(summary = "List providers by status", description = "Defaults to the review queue, oldest submission first.")
	PageResponse<ProviderSummary> list(
			@Parameter(description = "ONBOARDING, PENDING_REVIEW (default), APPROVED, or REJECTED")
			@RequestParam(name = "status", defaultValue = "PENDING_REVIEW") OnboardingStatus status,
			@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
			@RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(50) int size) {
		return this.reviews.list(status, page, size);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a provider's full profile for review")
	ProviderDetail get(@PathVariable UUID id) {
		return this.reviews.get(id);
	}

	@PostMapping("/{id}/approve")
	@Operation(summary = "Approve a provider", description = "Only from PENDING_REVIEW. Approved providers can receive jobs.")
	ProviderDetail approve(@CurrentUserId UUID adminId, @PathVariable UUID id) {
		return this.reviews.approve(adminId, id);
	}

	@PostMapping("/{id}/reject")
	@Operation(summary = "Reject a provider", description = "Only from PENDING_REVIEW. The reason is shown to the provider, who can fix and resubmit.")
	ProviderDetail reject(@CurrentUserId UUID adminId, @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
		return this.reviews.reject(adminId, id, request.reason());
	}

}
