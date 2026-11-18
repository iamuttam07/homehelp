package com.uel.homehelp.address;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AddressRepository extends JpaRepository<Address, UUID> {

	/** Default address first, then oldest first. */
	List<Address> findByUserIdOrderByDefaultAddressDescCreatedAtAsc(UUID userId);

	/** Scoped by owner, so one user can never read or change another user's address. */
	Optional<Address> findByIdAndUserId(UUID id, UUID userId);

	Optional<Address> findByUserIdAndDefaultAddressTrue(UUID userId);

	Optional<Address> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);

	long countByUserId(UUID userId);

	/**
	 * Locks the owner's row in {@code users} until the transaction ends. Every change
	 * to a user's addresses takes this lock first, so concurrent requests for the same
	 * user run one after another and the default and limit rules stay correct.
	 * Returns empty if the user no longer exists.
	 */
	@Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
	Optional<UUID> lockOwner(@Param("userId") UUID userId);

}
