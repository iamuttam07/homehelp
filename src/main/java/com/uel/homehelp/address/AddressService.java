package com.uel.homehelp.address;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uel.homehelp.common.web.ResourceNotFoundException;

/**
 * A user's addresses.
 *
 * <p>Rules: a user has at most {@link #MAX_ADDRESSES_PER_USER} addresses, and if they
 * have any, exactly one is the default. Every change first locks the user (see
 * {@link AddressRepository#lockOwner}), so concurrent requests can't break these rules.
 * The database also enforces "at most one default".
 */
@Service
class AddressService {

	static final int MAX_ADDRESSES_PER_USER = 10;

	private final AddressRepository addresses;

	AddressService(AddressRepository addresses) {
		this.addresses = addresses;
	}

	@Transactional(readOnly = true)
	List<Address> list(UUID userId) {
		return this.addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(userId);
	}

	@Transactional(readOnly = true)
	Address get(UUID userId, UUID addressId) {
		return this.addresses.findByIdAndUserId(addressId, userId).orElseThrow(() -> new ResourceNotFoundException("Address"));
	}

	/**
	 * Adds an address. The user's first address becomes their default.
	 * @throws AddressLimitReachedException if the user already has the maximum
	 */
	@Transactional
	Address create(UUID userId, AddressRequest request) {
		lockOwner(userId);
		long existing = this.addresses.countByUserId(userId);
		if (existing >= MAX_ADDRESSES_PER_USER) {
			throw new AddressLimitReachedException(MAX_ADDRESSES_PER_USER);
		}
		Address address = new Address(userId, existing == 0);
		address.apply(request);
		return this.addresses.saveAndFlush(address);
	}

	@Transactional
	Address update(UUID userId, UUID addressId, AddressRequest request) {
		Address address = get(userId, addressId);
		address.apply(request);
		return this.addresses.saveAndFlush(address);
	}

	/** Makes this address the default, and the previous default a normal address. */
	@Transactional
	Address makeDefault(UUID userId, UUID addressId) {
		lockOwner(userId);
		Address address = get(userId, addressId);
		if (!address.isDefaultAddress()) {
			this.addresses.findByUserIdAndDefaultAddressTrue(userId).ifPresent((previous) -> {
				previous.setDefaultAddress(false);
				// Flush first: the unique index allows only one default at a time.
				this.addresses.saveAndFlush(previous);
			});
			address.setDefaultAddress(true);
			this.addresses.saveAndFlush(address);
		}
		return address;
	}

	/** Deletes an address. If it was the default, the oldest remaining address takes over. */
	@Transactional
	void delete(UUID userId, UUID addressId) {
		lockOwner(userId);
		Address address = get(userId, addressId);
		this.addresses.delete(address);
		this.addresses.flush();
		if (address.isDefaultAddress()) {
			this.addresses.findFirstByUserIdOrderByCreatedAtAsc(userId)
				.ifPresent((next) -> next.setDefaultAddress(true));
		}
	}

	private void lockOwner(UUID userId) {
		this.addresses.lockOwner(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User"));
	}

}
