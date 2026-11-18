package com.uel.homehelp.address;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;

/** The logged-in user's own addresses. */
@RestController
@RequestMapping("/api/users/me/addresses")
class AddressController {

	private final AddressService service;

	AddressController(AddressService service) {
		this.service = service;
	}

	@GetMapping
	List<AddressResponse> list(@CurrentUserId UUID userId) {
		return this.service.list(userId).stream().map(AddressResponse::from).toList();
	}

	@PostMapping
	ResponseEntity<AddressResponse> create(@CurrentUserId UUID userId, @Valid @RequestBody AddressRequest request) {
		Address address = this.service.create(userId, request);
		return ResponseEntity.created(URI.create("/api/users/me/addresses/" + address.getId()))
			.body(AddressResponse.from(address));
	}

	@GetMapping("/{id}")
	AddressResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
		return AddressResponse.from(this.service.get(userId, id));
	}

	@PutMapping("/{id}")
	AddressResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
			@Valid @RequestBody AddressRequest request) {
		return AddressResponse.from(this.service.update(userId, id, request));
	}

	@PutMapping("/{id}/default")
	AddressResponse makeDefault(@CurrentUserId UUID userId, @PathVariable UUID id) {
		return AddressResponse.from(this.service.makeDefault(userId, id));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
		this.service.delete(userId, id);
		return ResponseEntity.noContent().build();
	}

}
