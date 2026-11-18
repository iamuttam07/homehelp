package com.uel.homehelp.user;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.uel.homehelp.common.Strings;

@Service
class UserService {

	private final UserRepository users;

	UserService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	User get(UUID userId) {
		return this.users.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User no longer exists"));
	}

	@Transactional
	User update(UUID userId, UpdateUserRequest request) {
		User user = get(userId);
		user.updateDetails(request.fullName().strip(), Strings.trimToNull(request.phone()));
		return this.users.saveAndFlush(user);
	}

}
