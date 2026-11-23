package com.uel.homehelp.booking;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uel.homehelp.auth.CurrentUserId;
import com.uel.homehelp.booking.SlotHoldDtos.HoldRequest;
import com.uel.homehelp.booking.SlotHoldDtos.HoldResponse;

@RestController
@RequestMapping("/api/slot-holds")
@Tag(name = "Booking")
class SlotHoldController {

	private final SlotHoldService holds;

	SlotHoldController(SlotHoldService holds) {
		this.holds = holds;
	}

	@PostMapping
	@Operation(summary = "Hold a slot",
			description = "Reserves a free provider for the chosen start time for 10 minutes while I book. "
					+ "Holding another slot replaces my previous hold. 409 if the time is no longer free.")
	ResponseEntity<HoldResponse> hold(@CurrentUserId UUID userId, @Valid @RequestBody HoldRequest request) {
		SlotHold hold = this.holds.hold(userId, request);
		return ResponseEntity.created(URI.create("/api/slot-holds/" + hold.holdId()))
			.body(HoldResponse.from(hold, SlotHoldService.zone(request.timeZone())));
	}

	@GetMapping("/current")
	@Operation(summary = "Get my active hold", description = "404 if I have none, or it has expired.")
	HoldResponse current(@CurrentUserId UUID userId,
			@RequestParam(name = "timeZone", defaultValue = SlotService.DEFAULT_TIME_ZONE) @Size(max = 50) String timeZone) {
		return HoldResponse.from(this.holds.current(userId), SlotHoldService.zone(timeZone));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Release my hold", description = "Frees the slot for others straight away.")
	ResponseEntity<Void> release(@CurrentUserId UUID userId, @PathVariable UUID id) {
		this.holds.release(userId, id);
		return ResponseEntity.noContent().build();
	}

}
