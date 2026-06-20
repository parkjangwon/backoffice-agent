package org.parkjw.agent.backoffice.catalog;

import java.time.Instant;
import java.util.List;

public record CatalogSnapshot(
		Instant scannedAt,
		String productName,
		String databaseProductName,
		String databaseProductVersion,
		List<DatabaseCatalog> databases,
		List<ScopeMapping> scopeMappings,
		CatalogScanMetadata metadata) {

	public CatalogSnapshot(
			Instant scannedAt,
			String productName,
			String databaseProductName,
			String databaseProductVersion,
			List<DatabaseCatalog> databases,
			List<ScopeMapping> scopeMappings) {
		this(scannedAt, productName, databaseProductName, databaseProductVersion, databases, scopeMappings, null);
	}

	public CatalogSnapshot {
		databases = databases == null ? List.of() : List.copyOf(databases);
		scopeMappings = scopeMappings == null ? List.of() : List.copyOf(scopeMappings);
		metadata = metadata == null ? CatalogScanMetadata.fromSnapshot(databases) : metadata;
	}

	public static CatalogSnapshot empty() {
		return new CatalogSnapshot(Instant.EPOCH, "unknown", "unknown", "unknown", List.of(), List.of(), CatalogScanMetadata.empty());
	}
}
