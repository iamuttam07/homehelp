package com.uel.homehelp.booking;

import static com.uel.homehelp.booking.BookingFixtures.LONDON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.uel.homehelp.TestUsers.LoggedInUser;

/** HTTP helpers shared by the booking API tests. */
final class BookingTestSupport {

	private final MockMvc mockMvc;

	BookingTestSupport(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	String holdId(LoggedInUser user, UUID addressId, LocalDate date, String londonTime) throws Exception {
		String body = post(user, "/api/slot-holds", "{\"service\": \"leak-repair\", \"addressId\": \"%s\", \"start\": \"%s\"}"
			.formatted(addressId, BookingFixtures.londonTime(date, londonTime)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.holdId");
	}

	ResultActions book(LoggedInUser user, String holdId) throws Exception {
		return post(user, "/api/bookings", "{\"holdId\": \"%s\"}".formatted(holdId));
	}

	/** Holds and books in one go; returns the booking id. */
	String holdAndBook(LoggedInUser user, UUID addressId, LocalDate date, String londonTime) throws Exception {
		String body = book(user, holdId(user, addressId, date, londonTime)).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	ResultActions post(LoggedInUser user, String path, String json) throws Exception {
		var request = MockMvcRequestBuilders.post(path).header(HttpHeaders.AUTHORIZATION, user.bearer());
		if (json != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return this.mockMvc.perform(request);
	}

	ResultActions get(LoggedInUser user, String path) throws Exception {
		return this.mockMvc.perform(MockMvcRequestBuilders.get(path).header(HttpHeaders.AUTHORIZATION, user.bearer()));
	}

	List<String> slotTimes(LoggedInUser user, UUID addressId, LocalDate date) throws Exception {
		List<String> starts = JsonPath.read(get(user,
				"/api/slots?service=leak-repair&date=%s&addressId=%s".formatted(date, addressId))
			.andReturn().getResponse().getContentAsString(), "$.slots[*].start");
		return starts.stream()
			.map((s) -> OffsetDateTime.parse(s).atZoneSameInstant(LONDON).toLocalTime().toString())
			.toList();
	}

	static String londonTimeOf(String body, String path) {
		return OffsetDateTime.parse(JsonPath.read(body, path)).atZoneSameInstant(LONDON).toLocalTime().toString();
	}

}
