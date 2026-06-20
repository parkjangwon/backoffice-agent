package org.parkjw.agent.backoffice.catalog;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.parkjw.agent.backoffice.config.AiQueryProperties;

public record CatalogStatusResponse(
		boolean snapshotPresent,
		Instant scannedAt,
		String catalogVersion,
		int databaseCount,
		int tableCount,
		int columnCount,
		int indexedTableCount,
		int emptyTableCount,
		List<String> includeDatabases,
		List<String> excludeDatabases,
		long lastScanDurationMillis,
		String storagePathBasename) {

	public CatalogStatusResponse {
		includeDatabases = includeDatabases == null ? List.of() : List.copyOf(includeDatabases);
		excludeDatabases = excludeDatabases == null ? List.of() : List.copyOf(excludeDatabases);
	}

	public static CatalogStatusResponse from(boolean snapshotPresent, CatalogSnapshot snapshot, AiQueryProperties properties) {
		var tables = snapshot.databases().stream()
				.flatMap(database -> database.tables().stream())
				.toList();
		var columnCount = tables.stream()
				.mapToInt(table -> table.columns().size())
				.sum();
		var indexedTableCount = (int) tables.stream()
				.filter(TableCatalog::indexed)
				.count();
		var emptyTableCount = (int) tables.stream()
				.filter(table -> table.estimatedRows() == 0)
				.count();
		return new CatalogStatusResponse(
				snapshotPresent,
				snapshot.scannedAt(),
				snapshot.metadata().catalogVersion(),
				snapshot.databases().size(),
				tables.size(),
				columnCount,
				indexedTableCount,
				emptyTableCount,
				properties.scan().includeDatabases(),
				properties.scan().excludeDatabases(),
				snapshot.metadata().scanDurationMillis(),
				Path.of(properties.catalog().storagePath()).getFileName().toString());
	}
}
