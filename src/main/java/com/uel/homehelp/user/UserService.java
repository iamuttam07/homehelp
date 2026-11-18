package com.uel.homehelp.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.uel.homehelp.common.Strings;
import com.uel.homehelp.common.web.ResourceNotFoundException;

@Service
class UserService {

	private final UserRepository users;

	UserService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	User get(UUID userId) {
		return this.users.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User"));
	}

	@Transactional
	User update(UUID userId, UpdateUserRequest request) {
		User user = get(userId);
		user.updateDetails(request.fullName().strip(), Strings.trimToNull(request.phone()));
		return this.users.saveAndFlush(user);
	}

}
