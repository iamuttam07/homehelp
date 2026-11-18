package com.uel.homehelp.address;

import java.time.Instant;
import java.util.UUID;

record AddressResponse(UUID id, String label, String line1, String line2, String city, String postcode,
		String countryCode, double latitude, double longitude, boolean isDefault, Instant createdAt,
		Instant updatedAt) {

	static AddressResponse from(Address address) {
		return new AddressResponse(address.getId(), address.getLabel(), address.getLine1(), address.getLine2(),
				address.getCity(), address.getPostcode(), address.getCountryCode(), address.getLatitude(),
				address.getLongitude(), address.isDefaultAddress(), address.getCreatedAt(), address.getUpdatedAt());
	}

}
