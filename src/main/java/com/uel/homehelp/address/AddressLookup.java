package com.uel.homehelp.address;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to addresses for other packages, always scoped to the owner. */
@Component
public class AddressLookup {

	private final AddressRepository addresses;

	AddressLookup(AddressRepository addresses) {
		this.addresses = addresses;
	}

	/** The address, if it exists and belongs to this user. */
	@Transactional(readOnly = true)
	public Optional<AddressPoint> findOwnedBy(UUID userId, UUID addressId) {
		return this.addresses.findByIdAndUserId(addressId, userId)
			.map((a) -> new AddressPoint(a.getId(), a.getLatitude(), a.getLongitude()));
	}

}
