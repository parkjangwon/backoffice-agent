package org.parkjw.agent.backoffice.catalog;

import java.util.List;

public record TableCatalog(
		String databaseName,
		String name,
		String type,
		String remarks,
		Long estimatedRows,
		List<ColumnCatalog> columns,
		List<String> primaryKeys,
		List<String> indexes,
		TableRole role,
		Double roleConfidence,
		String rowEstimateSource,
		Boolean indexed,
		List<String> usableIndexColumns) {

	public TableCatalog(
			String databaseName,
			String name,
			String type,
			String remarks,
			Long estimatedRows,
			List<ColumnCatalog> columns,
			List<String> primaryKeys,
			List<String> indexes) {
		this(
				databaseName,
				name,
				type,
				remarks,
				estimatedRows,
				columns,
				primaryKeys,
				indexes,
				null,
				null,
				null,
				false,
				List.of());
	}

	public TableCatalog {
		estimatedRows = estimatedRows == null ? -1 : estimatedRows;
		columns = columns == null ? List.of() : List.copyOf(columns);
		primaryKeys = primaryKeys == null ? List.of() : List.copyOf(primaryKeys);
		indexes = indexes == null ? List.of() : List.copyOf(indexes);
		usableIndexColumns = usableIndexColumns == null ? List.of() : List.copyOf(usableIndexColumns);
		var resolverInput = new TableRoleResolver.Input(name, remarks, estimatedRows, columns, primaryKeys, indexes, usableIndexColumns);
		role = role == null ? TableRoleResolver.role(resolverInput) : role;
		roleConfidence = roleConfidence == null || roleConfidence <= 0
				? TableRoleResolver.confidence(resolverInput)
				: Math.min(1, roleConfidence);
		rowEstimateSource = rowEstimateSource == null || rowEstimateSource.isBlank()
				? defaultRowEstimateSource(estimatedRows)
				: rowEstimateSource;
		indexed = Boolean.TRUE.equals(indexed) || !indexes.isEmpty() || !usableIndexColumns.isEmpty();
	}

	private static String defaultRowEstimateSource(long estimatedRows) {
		return estimatedRows >= 0 ? "snapshot_default" : "unavailable";
	}
}
