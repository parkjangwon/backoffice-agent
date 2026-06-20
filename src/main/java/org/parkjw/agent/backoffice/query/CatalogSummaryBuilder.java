package org.parkjw.agent.backoffice.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.parkjw.agent.backoffice.catalog.CatalogSearchIndex;
import org.parkjw.agent.backoffice.catalog.IndexedTable;
import org.parkjw.agent.backoffice.security.AccessContext;

import org.springframework.stereotype.Component;

@Component
public class CatalogSummaryBuilder {

	private static final Pattern PARTITION_SUFFIX = Pattern.compile(".*\\d{6}$");
	private static final Pattern EMAIL_COLUMN = Pattern.compile(".*(emailid|emailaddress|emailaddr|mailaddress|mailaddr|email|mail)$");

	private final CatalogScopeResolver scopeResolver;

	public CatalogSummaryBuilder(CatalogScopeResolver scopeResolver) {
		this.scopeResolver = scopeResolver;
	}

	public String build(AccessContext accessContext, BusinessIntent intent, CatalogSearchIndex index) {
		var builder = new StringBuilder();
		var relevantMappings = index.scopeMappingsForValues(accessContext.scopeValues()).stream()
				.limit(30)
				.toList();
		if (!relevantMappings.isEmpty()) {
			builder.append("Configured request-scope mappings(scope value -> catalog):\n");
			for (var mapping : relevantMappings) {
				builder.append("- ")
						.append(mapping.scopeValue())
						.append(" -> ")
						.append(mapping.mappedDatabase())
						.append('\n');
			}
		}
		var targetDatabases = scopeResolver.targetDatabases(accessContext, index.snapshot());
		appendScopeRelationshipHints(builder, index, targetDatabases, intent);
		var emittedTables = 0;
		for (var database : candidateTablesByDatabase(index, targetDatabases, intent).entrySet()) {
			var hasNonEmptyTables = database.getValue().stream().anyMatch(table -> table.estimatedRows() > 0);
			var serviceTables = database.getValue().stream()
					.filter(table -> matchesRequestedPartition(table, intent))
					.filter(table -> !hasNonEmptyTables || table.estimatedRows() != 0)
					.map(table -> new ScoredTable(table, tableScore(table, intent)))
					.sorted(Comparator.comparingInt(ScoredTable::score).reversed())
					.limit(40)
					.toList();
			if (serviceTables.isEmpty()) {
				continue;
			}
			builder.append("database ").append(database.getKey()).append('\n');
			for (var scoredTable : serviceTables) {
				var table = scoredTable.table().table();
				builder.append("- ")
						.append(table.name())
						.append(" rows~")
						.append(table.estimatedRows())
						.append(" pk=")
						.append(table.primaryKeys())
						.append(" indexes=")
						.append(table.indexes().stream().limit(8).toList())
						.append(" columns=(");
				var columns = table.columns().stream()
						.limit(30)
						.map(column -> column.name()
								+ ":"
								+ column.typeName()
								+ "{semantic="
								+ column.semanticType()
								+ ",unit="
								+ column.unit()
								+ "}"
								+ (column.remarks() == null || column.remarks().isBlank() ? "" : "[" + column.remarks() + "]"))
						.toList();
				builder.append(String.join(", ", columns)).append(")\n");
				emittedTables++;
				if (emittedTables >= 120) {
					return builder.toString();
				}
			}
		}
		return builder.isEmpty() ? "No scanned catalog yet. Use obvious sample tables if available." : builder.toString();
	}

	private void appendScopeRelationshipHints(
			StringBuilder builder,
			CatalogSearchIndex index,
			Set<String> targetDatabases,
			BusinessIntent intent) {
		var hints = index.scopeRelationshipHints(targetDatabases, intent.catalogTokens());
		if (hints.isEmpty()) {
			return;
		}
		builder.append("Scope relationship hints inferred from schema metadata:\n");
		for (var hint : hints.stream().limit(30).toList()) {
			builder.append("- ")
					.append(hint.summary())
					.append(" concept=")
					.append(hint.conceptToken())
					.append(" confidence=")
					.append("%.2f".formatted(hint.confidence()))
					.append(" evidence=")
					.append(hint.evidence())
					.append('\n');
		}
	}

	private LinkedHashMap<String, java.util.List<IndexedTable>> candidateTablesByDatabase(
			CatalogSearchIndex index,
			Set<String> targetDatabases,
			BusinessIntent intent) {
		var grouped = new LinkedHashMap<String, java.util.List<IndexedTable>>();
		for (var table : index.candidateTables(targetDatabases, intent.catalogTokens())) {
			grouped.computeIfAbsent(table.databaseName(), ignored -> new ArrayList<>()).add(table);
		}
		return grouped;
	}

	private int tableScore(IndexedTable indexedTable, BusinessIntent intent) {
		var table = indexedTable.table();
		var score = 0;
		var tableName = indexedTable.normalizedTableName();
		if (containsAny(tableName, "log", "history", "stat", "event", "audit", "usage", "user", "member")) {
			score += 20;
		}
		score += tokenScore(tableName, intent.catalogTokens()) * 15;
		for (var column : table.columns()) {
			var columnName = normalized(column.name());
			score += tokenScore(columnName, intent.catalogTokens()) * 15;
			if (emailPattern(columnName)) {
				score += 40;
			}
			if (column.scopeCandidate()) {
				score += 25;
			}
			if (containsAny(columnName, "date", "time", "created", "insert", "login", "sent", "received")) {
				score += 10;
			}
		}
		score += Math.min(table.indexes().size(), 10);
		score += Math.min(table.primaryKeys().size(), 5);
		score += intentScore(indexedTable, intent);
		if (table.estimatedRows() == 0) {
			score -= 120;
		}
		return score;
	}

	private boolean matchesRequestedPartition(IndexedTable table, BusinessIntent intent) {
		return intent.requestedYearMonths().isEmpty()
				|| table.partitionYearMonth() == null
				|| intent.requestedYearMonths().contains(table.partitionYearMonth());
	}

	private int intentScore(IndexedTable table, BusinessIntent intent) {
		var score = 0;
		if (intent.hasExactEmail() && table.hasEmailColumn()) {
			score += 45;
		}
		if ((intent.hasExactEmail() || intent.userScoped()) && table.hasUserKeyColumn()) {
			score += 25;
		}
		if (intent.hasExactEmail() && table.hasSenderRecipientPair()) {
			score += 25;
		}
		if ((intent.hasExactEmail() || intent.userScoped()) && table.hasDateTimeColumn()) {
			score += 15;
		}
		if ((intent.hasExactEmail() || intent.userScoped()) && table.hasUserKeyColumn() && table.hasSenderRecipientPair()) {
			score += 35;
		}
		if ((intent.hasExactEmail() || intent.userScoped()) && PARTITION_SUFFIX.matcher(table.normalizedTableName()).matches()) {
			score -= 20;
		}
		return score;
	}

	private int tokenScore(String value, Set<String> intentTokens) {
		var score = 0;
		for (var token : intentTokens) {
			if (value.contains(token)) {
				score++;
			}
		}
		return score;
	}

	private boolean emailPattern(String value) {
		return EMAIL_COLUMN.matcher(value).matches();
	}

	private boolean containsAny(String value, String... tokens) {
		for (var token : tokens) {
			if (value.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private String normalized(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "");
	}

	private record ScoredTable(IndexedTable table, int score) {
	}
}
