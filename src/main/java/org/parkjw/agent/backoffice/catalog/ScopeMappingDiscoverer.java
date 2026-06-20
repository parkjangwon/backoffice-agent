package org.parkjw.agent.backoffice.catalog;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ScopeMappingDiscoverer {

	private final ScanPatternMatcher matcher;

	public ScopeMappingDiscoverer(ScanPatternMatcher matcher) {
		this.matcher = matcher;
	}

	public List<ScopeMapping> discover(Connection connection, List<DatabaseCatalog> databases) {
		var mappings = new ArrayList<ScopeMapping>();
		var scannedDatabaseNames = databases.stream()
				.map(DatabaseCatalog::name)
				.map(value -> value.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
		for (var database : databases) {
			for (var table : database.tables()) {
				var scopeValueColumn = scopeValueColumn(table);
				var mappedDatabaseColumn = mappedDatabaseColumn(table);
				if (scopeValueColumn.isEmpty() || mappedDatabaseColumn.isEmpty() || table.estimatedRows() > 100_000) {
					continue;
				}
				mappings.addAll(discover(connection, table, scopeValueColumn.get(), mappedDatabaseColumn.get(), scannedDatabaseNames));
			}
		}
		return List.copyOf(mappings);
	}

	private List<ScopeMapping> discover(
			Connection connection,
			TableCatalog table,
			ColumnCatalog scopeValueColumn,
			ColumnCatalog mappedDatabaseColumn,
			Set<String> scannedDatabaseNames) {
		var mappings = new ArrayList<ScopeMapping>();
		var sql = """
				select distinct %s as scope_value, %s as mapped_database
				from %s
				where %s is not null
				  and %s <> ''
				  and %s is not null
				  and %s <> ''
				limit 10000
				""".formatted(
				quoteIdentifier(scopeValueColumn.name()),
				quoteIdentifier(mappedDatabaseColumn.name()),
				quoteQualified(table.databaseName(), table.name()),
				quoteIdentifier(scopeValueColumn.name()),
				quoteIdentifier(scopeValueColumn.name()),
				quoteIdentifier(mappedDatabaseColumn.name()),
				quoteIdentifier(mappedDatabaseColumn.name()));
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				var scopeValue = resultSet.getString("scope_value");
				var mappedDatabase = resultSet.getString("mapped_database");
				if (scopeValueLooksUsable(scopeValue) && mappedDatabaseExists(mappedDatabase, scannedDatabaseNames)) {
					mappings.add(new ScopeMapping(scopeValue, mappedDatabase, table.databaseName(), mappedDatabase));
				}
			}
		}
		catch (SQLException exception) {
			log.debug(
					"Scope mapping discovery skipped. table={}.{} reason={}",
					table.databaseName(),
					table.name(),
					exception.getMessage());
			return List.of();
		}
		return List.copyOf(mappings);
	}

	private Optional<ColumnCatalog> scopeValueColumn(TableCatalog table) {
		return table.columns().stream()
				.filter(column -> scopeValueLooksLikeColumn(column.name()))
				.findFirst();
	}

	private Optional<ColumnCatalog> mappedDatabaseColumn(TableCatalog table) {
		return table.columns().stream()
				.filter(column -> mappedDatabaseLooksLikeColumn(column.name()))
				.findFirst();
	}

	private boolean scopeValueLooksLikeColumn(String columnName) {
		var normalized = normalize(columnName);
		return normalized.equals("domain")
				|| normalized.equals("domainname")
				|| normalized.equals("scope")
				|| normalized.equals("scopevalue")
				|| normalized.equals("company")
				|| normalized.equals("organization")
				|| normalized.equals("customer")
				|| normalized.equals("workspace")
				|| normalized.endsWith("domain")
				|| normalized.endsWith("domainname")
				|| normalized.endsWith("scope")
				|| normalized.endsWith("scopevalue");
	}

	private boolean mappedDatabaseLooksLikeColumn(String columnName) {
		var normalized = normalize(columnName);
		return normalized.equals("tenant")
				|| normalized.equals("tenantid")
				|| normalized.equals("tenantdatabase")
				|| normalized.equals("schema")
				|| normalized.equals("schemaname")
				|| normalized.equals("dbname")
				|| normalized.equals("databasename");
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase().replace("_", "");
	}

	private boolean scopeValueLooksUsable(String value) {
		return value != null && !value.isBlank() && value.length() <= 255;
	}

	private boolean mappedDatabaseExists(String value, Set<String> scannedDatabaseNames) {
		return value != null
				&& !value.isBlank()
				&& scannedDatabaseNames.contains(value.toLowerCase(Locale.ROOT))
				&& matcher.shouldScan(value);
	}

	private String quoteIdentifier(String value) {
		return "`" + value.replace("`", "``") + "`";
	}

	private String quoteQualified(String databaseName, String tableName) {
		if (databaseName == null || databaseName.isBlank()) {
			return quoteIdentifier(tableName);
		}
		return quoteIdentifier(databaseName) + "." + quoteIdentifier(tableName);
	}
}
