package com.uel.homehelp.user;

import java.util.UUID;

/**
 * Published inside the registration transaction, so listeners can set up
 * related data (such as a provider profile) atomically with the new account.
 */
public record UserRegisteredEvent(UUID userId, Role role) {
}
