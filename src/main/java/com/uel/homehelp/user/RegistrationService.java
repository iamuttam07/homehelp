package com.uel.homehelp.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Creates a new account.
	 * @throws EmailAlreadyRegisteredException if the email is already taken
	 */
	@Transactional
	public User register(RegistrationRequest request) {
		String email = Emails.normalize(request.email());
		if (this.users.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException(null);
		}
		User user = new User(email, this.passwordEncoder.encode(request.password()), request.fullName().strip(),
				blankToNull(request.phone()), Role.valueOf(request.role()));
		try {
			return this.users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Another request registered the same email between the check and the insert.
			throw new EmailAlreadyRegisteredException(ex);
		}
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
