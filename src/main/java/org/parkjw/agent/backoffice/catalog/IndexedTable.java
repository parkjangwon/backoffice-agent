package org.parkjw.agent.backoffice.catalog;

import java.util.Set;

public record IndexedTable(
		String databaseName,
		String normalizedDatabaseName,
		TableCatalog table,
		String normalizedTableName,
		String partitionYearMonth,
		Set<String> tokens,
		Set<ColumnSemanticType> semanticTypes,
		boolean hasEmailColumn,
		boolean hasScopeColumn,
		boolean hasDateTimeColumn,
		boolean hasUserKeyColumn,
		boolean hasSenderRecipientPair) {

	public IndexedTable {
		tokens = tokens == null ? Set.of() : Set.copyOf(tokens);
		semanticTypes = semanticTypes == null ? Set.of() : Set.copyOf(semanticTypes);
	}

	public String tableName() {
		return table.name();
	}

	public long estimatedRows() {
		return table.estimatedRows();
	}

	public String qualifiedName() {
		return databaseName + "." + table.name();
	}
}
