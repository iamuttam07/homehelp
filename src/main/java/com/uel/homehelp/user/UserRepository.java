package com.uel.homehelp.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** Looks up a user by an already-normalized (trimmed, lower-case) email. */
	Optional<User> findByEmail(String email);

	/** Checks an already-normalized (trimmed, lower-case) email. */
	boolean existsByEmail(String email);

}
