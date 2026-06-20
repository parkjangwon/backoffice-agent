package org.parkjw.agent.backoffice.query;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.config.AiQueryProperties;
import org.parkjw.agent.backoffice.security.AccessContext;

import org.springframework.stereotype.Component;

@Component
public class QueryResultCache {

	private final AiQueryProperties properties;
	private final Clock clock;
	private final ConcurrentHashMap<Key, Entry> entries = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Key, Object> inFlightLocks = new ConcurrentHashMap<>();

	public QueryResultCache(AiQueryProperties properties) {
		this.properties = properties;
		this.clock = Clock.systemUTC();
	}

	public Optional<QueryResult> get(Key key) {
		if (!properties.cache().enabled()) {
			return Optional.empty();
		}
		var entry = entries.get(key);
		if (entry == null) {
			return Optional.empty();
		}
		if (entry.expiresAt().isBefore(Instant.now(clock))) {
			entries.remove(key, entry);
			return Optional.empty();
		}
		return Optional.of(entry.result());
	}

	public Lookup getOrCompute(Key key, Supplier<QueryResult> supplier) {
		var cached = get(key);
		if (cached.isPresent()) {
			return new Lookup(cached.get(), true);
		}
		return computeIfAbsent(key, supplier);
	}

	public Lookup computeIfAbsent(Key key, Supplier<QueryResult> supplier) {
		if (!properties.cache().enabled()) {
			return new Lookup(supplier.get(), false);
		}
		var lock = inFlightLocks.computeIfAbsent(key, ignored -> new Object());
		try {
			synchronized (lock) {
				var cached = get(key);
				if (cached.isPresent()) {
					return new Lookup(cached.get(), true);
				}
				var result = supplier.get();
				put(key, result);
				return new Lookup(result, false);
			}
		}
		finally {
			inFlightLocks.remove(key, lock);
		}
	}

	public void put(Key key, QueryResult result) {
		if (!properties.cache().enabled()) {
			return;
		}
		evictExpired();
		if (entries.size() >= properties.cache().maxEntries()) {
			evictOldest();
		}
		entries.put(key, new Entry(result, Instant.now(clock).plusSeconds(properties.cache().ttlSeconds())));
	}

	public static Key key(
			String action,
			QueryRequest request,
			AccessContext accessContext,
			CatalogSnapshot catalog,
			AiQueryProperties properties,
			int limit) {
		return new Key(
				normalize(action),
				normalize(accessContext.actorId()),
				accessContext.role().name(),
				normalizedList(accessContext.scopeValues()),
				normalizedList(accessContext.allowedDatabases()),
				trimToEmpty(request.prompt()),
				limit,
				catalog.scannedAt());
	}

	private void evictExpired() {
		var now = Instant.now(clock);
		entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
	}

	private void evictOldest() {
		entries.entrySet().stream()
				.min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
				.ifPresent(entry -> entries.remove(entry.getKey(), entry.getValue()));
	}

	private static List<String> normalizedList(List<String> values) {
		return values.stream()
				.map(QueryResultCache::normalize)
				.sorted()
				.toList();
	}

	private static String normalize(String value) {
		return trimToEmpty(value).toLowerCase(Locale.ROOT);
	}

	private static String trimToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	public record Key(
			String action,
			String actorId,
			String role,
			List<String> scopeValues,
			List<String> allowedDatabases,
			String prompt,
			int limit,
			Instant catalogScannedAt) {
	}

	private record Entry(QueryResult result, Instant expiresAt) {
	}

	public record Lookup(QueryResult result, boolean cacheHit) {
	}
}
