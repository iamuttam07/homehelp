package com.uel.homehelp.address;

import java.util.UUID;

/** A full address, for features that keep a copy (e.g. bookings). */
public record AddressDetails(UUID id, String line1, String line2, String city, String postcode, String countryCode,
		double latitude, double longitude) {
}
