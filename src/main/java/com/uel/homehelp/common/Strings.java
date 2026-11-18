package com.uel.homehelp.common;

/** Small string helpers shared across features. */
public final class Strings {

	private Strings() {
	}

	/** Strips surrounding whitespace; returns {@code null} for null or blank input. */
	public static String trimToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
