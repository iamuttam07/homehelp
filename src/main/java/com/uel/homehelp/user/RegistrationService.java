package com.uel.homehelp.user;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.common.Strings;

@Service
public class RegistrationService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final ApplicationEventPublisher events;

	RegistrationService(UserRepository users, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
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
				Strings.trimToNull(request.phone()), Role.valueOf(request.role()));
		try {
			user = this.users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Another request registered the same email between the check and the insert.
			throw new EmailAlreadyRegisteredException(ex);
		}
		this.events.publishEvent(new UserRegisteredEvent(user.getId(), user.getRole()));
		return user;
	}

}
