package com.uel.homehelp.user;

/**
 * What a user can do in HomeHelp. Stored as text in {@code users.role}.
 */
public enum Role {

	/** Books services. */
	CUSTOMER,

	/** Offers and performs services. */
	PROVIDER,

	/** Runs the platform. Cannot be chosen at registration. */
	ADMIN

}
