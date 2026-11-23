package com.uel.homehelp.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Keeps slot holds in Redis.
 *
 * <p>Each provider's time is split into 15-minute <em>cells</em>. A hold claims every
 * cell its job covers ({@code homehelp:hold:cell:<providerId>:<cell>} → hold id), plus
 * the hold itself as JSON and a pointer from the customer to their hold. All keys share
 * the same expiry, so an abandoned hold disappears completely on its own.
 *
 * <p>Claiming and releasing run as Lua scripts, which Redis executes atomically: two
 * customers can never claim the same provider cell.
 */
@Component
class SlotHoldStore {

	static final long CELL_SECONDS = 15 * 60;

	private static final String PREFIX = "homehelp:hold:";

	/**
	 * KEYS[1] hold, KEYS[2] user pointer, KEYS[3..] cells. ARGV[1] hold id, ARGV[2] ttl
	 * in ms, ARGV[3] hold JSON, ARGV[4] the customer's previous hold id (its cells may be
	 * reused) or ''. Returns 1 if claimed, 0 if any cell is taken by someone else.
	 */
	private static final RedisScript<Long> CLAIM = new DefaultRedisScript<>("""
			for i = 3, #KEYS do
			  local owner = redis.call('GET', KEYS[i])
			  if owner and owner ~= ARGV[4] then return 0 end
			end
			for i = 3, #KEYS do
			  redis.call('SET', KEYS[i], ARGV[1], 'PX', ARGV[2])
			end
			redis.call('SET', KEYS[1], ARGV[3], 'PX', ARGV[2])
			redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
			return 1
			""", Long.class);

	/** Deletes the hold and only the cells and pointer that still belong to it. */
	private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
			for i = 3, #KEYS do
			  if redis.call('GET', KEYS[i]) == ARGV[1] then redis.call('DEL', KEYS[i]) end
			end
			redis.call('DEL', KEYS[1])
			if redis.call('GET', KEYS[2]) == ARGV[1] then redis.call('DEL', KEYS[2]) end
			return 1
			""", Long.class);

	private final StringRedisTemplate redis;

	private final JsonMapper json;

	SlotHoldStore(StringRedisTemplate redis, JsonMapper json) {
		this.redis = redis;
		this.json = json;
	}

	/**
	 * Claims the provider's cells for the hold.
	 * @param replaceableHoldId the customer's current hold, whose cells may be taken over
	 * @return false if another customer holds any of the cells
	 */
	boolean claim(SlotHold hold, Duration ttl, UUID replaceableHoldId) {
		List<String> keys = new ArrayList<>(List.of(holdKey(hold.holdId()), userKey(hold.userId())));
		keys.addAll(cellKeys(hold.providerId(), hold.start(), hold.end()));
		Long result = this.redis.execute(CLAIM, keys, hold.holdId().toString(), String.valueOf(ttl.toMillis()),
				this.json.writeValueAsString(hold), (replaceableHoldId != null) ? replaceableHoldId.toString() : "");
		return Long.valueOf(1).equals(result);
	}

	void release(SlotHold hold) {
		List<String> keys = new ArrayList<>(List.of(holdKey(hold.holdId()), userKey(hold.userId())));
		keys.addAll(cellKeys(hold.providerId(), hold.start(), hold.end()));
		this.redis.execute(RELEASE, keys, hold.holdId().toString());
	}

	Optional<SlotHold> find(UUID holdId) {
		String value = this.redis.opsForValue().get(holdKey(holdId));
		return Optional.ofNullable(value).map((v) -> this.json.readValue(v, SlotHold.class));
	}

	/** The customer's active hold, if any. */
	Optional<SlotHold> findByUser(UUID userId) {
		String holdId = this.redis.opsForValue().get(userKey(userId));
		return Optional.ofNullable(holdId).flatMap((id) -> find(UUID.fromString(id)));
	}

	/**
	 * Which cells are held for each provider between {@code from} and {@code to}.
	 * @param ignoreHoldId a hold to treat as free (the caller's own), or null
	 * @return provider id → start instants of held cells
	 */
	Map<UUID, Set<Instant>> heldCells(Collection<UUID> providerIds, Instant from, Instant to, UUID ignoreHoldId) {
		Map<UUID, Set<Instant>> held = new HashMap<>();
		if (providerIds.isEmpty()) {
			return held;
		}
		List<UUID> owners = new ArrayList<>();
		List<Instant> cells = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		for (UUID providerId : providerIds) {
			for (long cell = cellIndex(from); cell < cellIndex(to.plusSeconds(CELL_SECONDS - 1)); cell++) {
				owners.add(providerId);
				cells.add(Instant.ofEpochSecond(cell * CELL_SECONDS));
				keys.add(cellKey(providerId, cell));
			}
		}
		List<String> values = this.redis.opsForValue().multiGet(keys);
		String ignore = (ignoreHoldId != null) ? ignoreHoldId.toString() : null;
		for (int i = 0; i < keys.size(); i++) {
			String owner = (values != null) ? values.get(i) : null;
			if (owner != null && !owner.equals(ignore)) {
				held.computeIfAbsent(owners.get(i), (k) -> new HashSet<>()).add(cells.get(i));
			}
		}
		return held;
	}

	/** Whether a job from {@code start} to {@code end} avoids every held cell. */
	static boolean isFree(Set<Instant> heldCells, Instant start, Instant end) {
		if (heldCells == null || heldCells.isEmpty()) {
			return true;
		}
		for (long cell = cellIndex(start); cell * CELL_SECONDS < end.getEpochSecond(); cell++) {
			if (heldCells.contains(Instant.ofEpochSecond(cell * CELL_SECONDS))) {
				return false;
			}
		}
		return true;
	}

	/** Start of the 15-minute cell that contains the instant. */
	static Instant cellStart(Instant instant) {
		return Instant.ofEpochSecond(cellIndex(instant) * CELL_SECONDS);
	}

	private static long cellIndex(Instant instant) {
		return Math.floorDiv(instant.getEpochSecond(), CELL_SECONDS);
	}

	private static List<String> cellKeys(UUID providerId, Instant start, Instant end) {
		List<String> keys = new ArrayList<>();
		for (long cell = cellIndex(start); cell * CELL_SECONDS < end.getEpochSecond(); cell++) {
			keys.add(cellKey(providerId, cell));
		}
		return keys;
	}

	private static String cellKey(UUID providerId, long cell) {
		return PREFIX + "cell:" + providerId + ":" + cell;
	}

	private static String holdKey(UUID holdId) {
		return PREFIX + holdId;
	}

	private static String userKey(UUID userId) {
		return PREFIX + "user:" + userId;
	}

}
