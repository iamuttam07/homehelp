package com.uel.homehelp.provider;

/**
 * Where a provider is in onboarding. Allowed changes:
 *
 * <pre>
 * ONBOARDING ──submit──► PENDING_REVIEW ──approve──► APPROVED
 *     ▲                       │
 *     │                       └──reject──► REJECTED ──submit──► PENDING_REVIEW
 * </pre>
 *
 * Only {@link #APPROVED} providers can receive jobs.
 */
public enum OnboardingStatus {

	ONBOARDING,

	PENDING_REVIEW,

	APPROVED,

	REJECTED

}
