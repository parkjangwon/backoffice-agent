package org.parkjw.agent.backoffice.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "ai-query")
public record AiQueryProperties(
		Security security,
		Catalog catalog,
		Audit audit,
		Scan scan,
		Policy policy,
		DataPolicy dataPolicy,
		Cache cache,
		Ai ai) {

	public AiQueryProperties(
			Security security,
			Catalog catalog,
			Audit audit,
			Scan scan,
			Policy policy,
			DataPolicy dataPolicy,
			Cache cache) {
		this(security, catalog, audit, scan, policy, dataPolicy, cache, null);
	}

	@ConstructorBinding
	public AiQueryProperties {
		security = security == null ? new Security(true, List.of(), List.of(), false, "", 300) : security;
		catalog = catalog == null ? new Catalog("data/catalog-snapshot.json") : catalog;
		audit = audit == null ? new Audit("data/audit-events.jsonl") : audit;
		scan = scan == null ? new Scan(List.of(), List.of("information_schema", "mysql", "performance_schema", "sys")) : scan;
		policy = policy == null ? new Policy(100, 1000, 10, 5000) : policy;
		dataPolicy = dataPolicy == null ? new DataPolicy(true) : dataPolicy;
		cache = cache == null ? new Cache(true, 60, 200) : cache;
		ai = ai == null ? new Ai(AiProvider.OLLAMA, "", "", 0.0) : ai;
	}

	public record Security(
			boolean apiKeyEnabled,
			List<String> apiKeys,
			List<String> allowedIps,
			boolean requestSigningEnabled,
			String requestSigningSecret,
			long requestSigningToleranceSeconds) {

		public Security {
			apiKeys = apiKeys == null ? List.of() : apiKeys.stream()
					.filter(value -> value != null && !value.isBlank())
					.toList();
			allowedIps = allowedIps == null ? List.of() : allowedIps.stream()
					.filter(value -> value != null && !value.isBlank())
					.toList();
			requestSigningSecret = requestSigningSecret == null ? "" : requestSigningSecret.strip();
			requestSigningToleranceSeconds = requestSigningToleranceSeconds <= 0 ? 300 : requestSigningToleranceSeconds;
		}
	}

	public record Catalog(String storagePath) {

		public Catalog {
			storagePath = storagePath == null || storagePath.isBlank()
					? "data/catalog-snapshot.json"
					: storagePath;
		}
	}

	public record Audit(String storagePath) {

		public Audit {
			storagePath = storagePath == null || storagePath.isBlank()
					? "data/audit-events.jsonl"
					: storagePath;
		}
	}

	public record Scan(List<String> includeDatabases, List<String> excludeDatabases) {

		public Scan {
			includeDatabases = includeDatabases == null ? List.of() : List.copyOf(includeDatabases);
			excludeDatabases = excludeDatabases == null ? List.of() : List.copyOf(excludeDatabases);
		}
	}

	public record Policy(int defaultLimit, int maxLimit, int queryTimeoutSeconds, int exportMaxRows) {

		public int clampLimit(Integer requestedLimit) {
			var requested = requestedLimit == null ? defaultLimit : requestedLimit;
			return Math.max(1, Math.min(requested, maxLimit));
		}
	}

	public record DataPolicy(boolean userNameEncrypted) {
	}

	public record Cache(boolean enabled, int ttlSeconds, int maxEntries) {

		public Cache {
			ttlSeconds = ttlSeconds <= 0 ? 60 : ttlSeconds;
			maxEntries = maxEntries <= 0 ? 200 : maxEntries;
		}
	}

	public record Ai(AiProvider provider, String model, String baseUrl, double temperature) {

		public Ai {
			provider = provider == null ? AiProvider.OLLAMA : provider;
			model = model == null ? "" : model.strip();
			baseUrl = baseUrl == null ? "" : baseUrl.strip();
			temperature = Math.max(0.0, temperature);
		}
	}

}
