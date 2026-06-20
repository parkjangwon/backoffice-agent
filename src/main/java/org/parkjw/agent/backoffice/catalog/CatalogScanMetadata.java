package org.parkjw.agent.backoffice.catalog;

import java.time.Duration;
import java.util.List;

public record CatalogScanMetadata(
		String catalogVersion,
		long scanDurationMillis,
		List<String> includedDatabaseNames,
		List<String> excludedDatabaseNames,
		List<String> scannedDatabaseNames,
		int scannedDatabaseCount,
		int scannedTableCount) {

	public static final String CATALOG_VERSION = "catalog-v2";

	public CatalogScanMetadata {
		catalogVersion = catalogVersion == null || catalogVersion.isBlank() ? CATALOG_VERSION : catalogVersion;
		scanDurationMillis = Math.max(0, scanDurationMillis);
		includedDatabaseNames = includedDatabaseNames == null ? List.of() : List.copyOf(includedDatabaseNames);
		excludedDatabaseNames = excludedDatabaseNames == null ? List.of() : List.copyOf(excludedDatabaseNames);
		scannedDatabaseNames = scannedDatabaseNames == null ? List.of() : List.copyOf(scannedDatabaseNames);
		scannedDatabaseCount = scannedDatabaseCount < 0 ? scannedDatabaseNames.size() : scannedDatabaseCount;
		scannedTableCount = Math.max(0, scannedTableCount);
	}

	public static CatalogScanMetadata empty() {
		return new CatalogScanMetadata(CATALOG_VERSION, 0, List.of(), List.of(), List.of(), 0, 0);
	}

	public static CatalogScanMetadata fromSnapshot(List<DatabaseCatalog> databases) {
		var safeDatabases = databases == null ? List.<DatabaseCatalog>of() : databases;
		var databaseNames = safeDatabases.stream()
				.map(DatabaseCatalog::name)
				.toList();
		return new CatalogScanMetadata(
				CATALOG_VERSION,
				0,
				databaseNames,
				List.of(),
				databaseNames,
				databaseNames.size(),
				tableCount(safeDatabases));
	}

	public static CatalogScanMetadata fromScan(Duration duration, List<DatabaseScope> discoveredScopes, List<DatabaseCatalog> databases) {
		var scannedNames = databases.stream()
				.map(DatabaseCatalog::name)
				.toList();
		var excludedNames = discoveredScopes.stream()
				.map(DatabaseScope::name)
				.filter(name -> !scannedNames.contains(name))
				.toList();
		return new CatalogScanMetadata(
				CATALOG_VERSION,
				duration.toMillis(),
				scannedNames,
				excludedNames,
				scannedNames,
				scannedNames.size(),
				tableCount(databases));
	}

	private static int tableCount(List<DatabaseCatalog> databases) {
		return databases.stream()
				.mapToInt(database -> database.tables().size())
				.sum();
	}
}
