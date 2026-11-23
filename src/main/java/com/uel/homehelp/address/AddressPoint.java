package com.uel.homehelp.address;

import java.util.UUID;

/** Where an address is, for features outside this package (e.g. finding slots). */
public record AddressPoint(UUID id, double latitude, double longitude) {
}
