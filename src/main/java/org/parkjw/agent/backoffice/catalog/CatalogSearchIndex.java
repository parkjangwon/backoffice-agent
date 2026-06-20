package org.parkjw.agent.backoffice.catalog;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record CatalogSearchIndex(
		CatalogSnapshot snapshot,
		List<IndexedTable> allTables,
		List<ScopeRelationshipHint> scopeRelationshipHints,
		Map<String, List<IndexedTable>> tablesByDatabase,
		Map<String, List<IndexedTable>> tablesByToken,
		Map<String, List<ScopeMapping>> scopeMappingsByValue) {

	public CatalogSearchIndex {
		allTables = allTables == null ? List.of() : List.copyOf(allTables);
		scopeRelationshipHints = scopeRelationshipHints == null ? List.of() : List.copyOf(scopeRelationshipHints);
		tablesByDatabase = tablesByDatabase == null ? Map.of() : Map.copyOf(tablesByDatabase);
		tablesByToken = tablesByToken == null ? Map.of() : Map.copyOf(tablesByToken);
		scopeMappingsByValue = scopeMappingsByValue == null ? Map.of() : Map.copyOf(scopeMappingsByValue);
	}

	public static CatalogSearchIndex empty() {
		return new CatalogSearchIndex(CatalogSnapshot.empty(), List.of(), List.of(), Map.of(), Map.of(), Map.of());
	}

	public List<ScopeMapping> scopeMappingsForValues(Collection<String> scopeValues) {
		if (scopeValues == null || scopeValues.isEmpty()) {
			return snapshot.scopeMappings();
		}
		var mappings = new LinkedHashSet<ScopeMapping>();
		for (var scopeValue : scopeValues) {
			mappings.addAll(scopeMappingsByValue.getOrDefault(normalize(scopeValue), List.of()));
		}
		return List.copyOf(mappings);
	}

	public List<IndexedTable> candidateTables(Set<String> databaseScope, Collection<String> tokens) {
		var scopedTables = scopedTables(databaseScope);
		if (tokens == null || tokens.isEmpty()) {
			return scopedTables;
		}
		var scopedNames = scopedQualifiedNames(scopedTables);
		var matches = new LinkedHashSet<IndexedTable>();
		for (var token : tokens) {
			matches.addAll(tablesByToken.getOrDefault(normalizeToken(token), List.of())
					.stream()
					.filter(table -> scopedNames.contains(table.qualifiedName()))
					.toList());
		}
		return matches.isEmpty() ? scopedTables : List.copyOf(matches);
	}

	public List<ScopeRelationshipHint> scopeRelationshipHints(Set<String> databaseScope, Collection<String> tokens) {
		var normalizedDatabases = normalizedValues(databaseScope);
		var normalizedTokens = normalizedTokens(tokens);
		return scopeRelationshipHints.stream()
				.filter(hint -> normalizedDatabases.isEmpty() || normalizedDatabases.contains(normalize(hint.databaseName())))
				.filter(hint -> normalizedTokens.isEmpty()
						|| normalizedTokens.contains(normalizeToken(hint.conceptToken()))
						|| normalizedTokens.contains(normalizeToken(hint.tableName()))
						|| normalizedTokens.contains(normalizeToken(hint.scopeTableName()))
						|| normalizedTokens.contains(normalizeToken(hint.keyColumnName())))
				.sorted(java.util.Comparator.comparingDouble(ScopeRelationshipHint::confidence).reversed())
				.limit(40)
				.toList();
	}

	private List<IndexedTable> scopedTables(Set<String> databaseScope) {
		if (databaseScope == null || databaseScope.isEmpty()) {
			return allTables;
		}
		var tables = new LinkedHashSet<IndexedTable>();
		for (var databaseName : databaseScope) {
			tables.addAll(tablesByDatabase.getOrDefault(normalize(databaseName), List.of()));
		}
		return List.copyOf(tables);
	}

	private Set<String> scopedQualifiedNames(List<IndexedTable> scopedTables) {
		return scopedTables.stream()
				.map(IndexedTable::qualifiedName)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private Set<String> normalizedValues(Collection<String> values) {
		if (values == null || values.isEmpty()) {
			return Set.of();
		}
		return values.stream()
				.map(CatalogSearchIndex::normalize)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private Set<String> normalizedTokens(Collection<String> values) {
		if (values == null || values.isEmpty()) {
			return Set.of();
		}
		return values.stream()
				.map(CatalogSearchIndex::normalizeToken)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	static String normalizeToken(String value) {
		return normalize(value).replace("_", "");
	}
}
