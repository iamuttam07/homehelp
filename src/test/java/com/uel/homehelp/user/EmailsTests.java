package com.uel.homehelp.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailsTests {

	@Test
	void normalizesByTrimmingAndLowerCasing() {
		assertThat(Emails.normalize("  Asha.Patel@Example.COM ")).isEqualTo("asha.patel@example.com");
	}

}
