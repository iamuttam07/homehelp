package com.uel.homehelp.user;

import java.util.Locale;

/**
 * Emails are stored and looked up trimmed and lower-cased, so they are
 * unique and matched case-insensitively.
 */
public final class Emails {

	private Emails() {
	}

	public static String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
