package org.parkjw.agent.backoffice.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class CatalogSearchIndexBuilder {

	private static final Pattern WORD_SPLIT = Pattern.compile("[^a-z0-9]+");
	private static final Pattern YEAR_MONTH_SUFFIX = Pattern.compile("(20\\d{2})(0[1-9]|1[0-2])$");
	private final ScopeRelationshipInferer scopeRelationshipInferer = new ScopeRelationshipInferer();

	public CatalogSearchIndex build(CatalogSnapshot snapshot) {
		var allTables = new ArrayList<IndexedTable>();
		var tablesByDatabase = new LinkedHashMap<String, List<IndexedTable>>();
		var tablesByToken = new LinkedHashMap<String, List<IndexedTable>>();
		var scopeMappingsByValue = new LinkedHashMap<String, List<ScopeMapping>>();

		for (var database : snapshot.databases()) {
			for (var table : database.tables()) {
				var indexedTable = indexedTable(database.name(), table);
				allTables.add(indexedTable);
				tablesByDatabase.computeIfAbsent(indexedTable.normalizedDatabaseName(), ignored -> new ArrayList<>()).add(indexedTable);
				for (var token : indexedTable.tokens()) {
					tablesByToken.computeIfAbsent(token, ignored -> new ArrayList<>()).add(indexedTable);
				}
			}
		}
		for (var mapping : snapshot.scopeMappings()) {
			scopeMappingsByValue.computeIfAbsent(normalize(mapping.scopeValue()), ignored -> new ArrayList<>()).add(mapping);
		}
		return new CatalogSearchIndex(
				snapshot,
				allTables,
				scopeRelationshipInferer.infer(allTables),
				immutableLists(tablesByDatabase),
				immutableLists(tablesByToken),
				immutableLists(scopeMappingsByValue));
	}

	private IndexedTable indexedTable(String databaseName, TableCatalog table) {
		var tokens = new LinkedHashSet<String>();
		var semanticTypes = new LinkedHashSet<ColumnSemanticType>();
		addTokens(tokens, databaseName);
		addTokens(tokens, table.name());
		addTokens(tokens, table.remarks());
		addTokens(tokens, table.role().name());
		for (var index : table.indexes()) {
			addTokens(tokens, index);
		}
		for (var primaryKey : table.primaryKeys()) {
			addTokens(tokens, primaryKey);
		}

		var hasEmailColumn = false;
		var hasScopeColumn = false;
		var hasDateTimeColumn = false;
		var hasUserKeyColumn = false;
		var hasSender = false;
		var hasRecipient = false;
		for (var column : table.columns()) {
			var columnName = normalizeToken(column.name());
			addTokens(tokens, column.name());
			addTokens(tokens, column.remarks());
			addTokens(tokens, column.displayName());
			addTokens(tokens, column.label());
			semanticTypes.add(column.semanticType());
			tokens.add(column.semanticType().name().toLowerCase(Locale.ROOT));
			tokens.add(column.unit().name().toLowerCase(Locale.ROOT));
			hasEmailColumn = hasEmailColumn || column.semanticType() == ColumnSemanticType.EMAIL || emailPattern(columnName);
			hasScopeColumn = hasScopeColumn || column.scopeCandidate() || column.semanticType() == ColumnSemanticType.DOMAIN;
			hasDateTimeColumn = hasDateTimeColumn || column.semanticType() == ColumnSemanticType.DATETIME || timePattern(columnName);
			hasUserKeyColumn = hasUserKeyColumn || userKeyPattern(columnName);
			hasSender = hasSender || containsAny(columnName, "sender", "adrfrom", "fromaddress", "mailfrom");
			hasRecipient = hasRecipient || containsAny(columnName, "recipient", "rcpt", "adrto", "toaddress", "mailto");
		}
		var normalizedTableName = normalizeToken(table.name());
		return new IndexedTable(
				databaseName,
				normalize(databaseName),
				table,
				normalizedTableName,
				partitionYearMonth(normalizedTableName),
				tokens,
				semanticTypes,
				hasEmailColumn,
				hasScopeColumn,
				hasDateTimeColumn,
				hasUserKeyColumn,
				hasSender && hasRecipient);
	}

	private void addTokens(Set<String> tokens, String value) {
		var compact = normalizeToken(value);
		if (!compact.isBlank()) {
			tokens.add(compact);
		}
		for (var token : WORD_SPLIT.split(normalize(value))) {
			var normalized = normalizeToken(token);
			if (normalized.length() > 1) {
				tokens.add(normalized);
			}
		}
	}

	private String partitionYearMonth(String normalizedTableName) {
		var matcher = YEAR_MONTH_SUFFIX.matcher(normalizedTableName);
		return matcher.find() ? matcher.group() : null;
	}

	private boolean emailPattern(String value) {
		return value.matches(".*(emailid|emailaddress|emailaddr|mailaddress|mailaddr|email|mail)$");
	}

	private boolean userKeyPattern(String value) {
		return containsAny(value, "useruid", "userid", "accountuid", "accountid", "memberuid", "memberid");
	}

	private boolean timePattern(String value) {
		return containsAny(value, "time", "date", "millis", "created", "insert", "sent", "received");
	}

	private boolean containsAny(String value, String... tokens) {
		for (var token : tokens) {
			if (value.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private String normalizeToken(String value) {
		return normalize(value).replace("_", "");
	}

	private <T> Map<String, List<T>> immutableLists(Map<String, List<T>> values) {
		var immutable = new LinkedHashMap<String, List<T>>();
		values.forEach((key, list) -> immutable.put(key, List.copyOf(list)));
		return Map.copyOf(immutable);
	}

}
