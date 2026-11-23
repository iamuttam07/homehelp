package com.uel.homehelp.provider;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for {@code /api/provider/availability}. */
final class AvailabilityDtos {

	private AvailabilityDtos() {
	}

	/**
	 * One working window.
	 *
	 * @param dayOfWeek {@code MONDAY} ... {@code SUNDAY}
	 * @param start start time, {@code HH:mm}, on a quarter hour
	 * @param end end time, {@code HH:mm}, on a quarter hour, after {@code start} on the same day
	 */
	record WindowDto(

			@NotNull
			DayOfWeek dayOfWeek,

			@NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "09:00")
			LocalTime start,

			@NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "17:00")
			LocalTime end) {

		static final Comparator<WindowDto> WEEK_ORDER = Comparator.comparing(WindowDto::dayOfWeek)
			.thenComparing(WindowDto::start);

		static WindowDto from(AvailabilityWindow window) {
			return new WindowDto(window.getDayOfWeek(), window.getStartTime(), window.getEndTime());
		}

	}

	/**
	 * Body of {@code PUT /api/provider/availability}: the whole week. An empty
	 * {@code windows} list means "not available".
	 *
	 * @param timeZone IANA time zone the windows are in, e.g. {@code Europe/London}
	 */
	record AvailabilityRequest(

			@NotBlank @Size(max = 50) @Schema(example = "Europe/London")
			String timeZone,

			@NotNull @Size(max = ProviderAvailabilityService.MAX_WINDOWS_PER_WEEK)
			List<@NotNull @Valid WindowDto> windows) {
	}

	/** The provider's week, sorted by day and start time. */
	record AvailabilityResponse(String timeZone, List<WindowDto> windows) {
	}

}
