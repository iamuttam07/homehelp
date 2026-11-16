package com.uel.homehelp.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class PasswordHashingConfig {

	/**
	 * BCrypt by default. Hashes are stored with an algorithm prefix such as
	 * {@code {bcrypt}}, so the algorithm can be upgraded later without breaking
	 * existing passwords.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
