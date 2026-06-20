package org.parkjw.agent.backoffice.catalog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class MySqlCatalogScanner {

	private final ScanPatternMatcher matcher;

	public MySqlCatalogScanner(ScanPatternMatcher matcher) {
		this.matcher = matcher;
	}

	public List<DatabaseCatalog> scan(Connection connection, List<DatabaseScope> scopes) throws SQLException {
		var includedScopes = scopes.stream()
				.filter(scope -> matcher.shouldScan(scope.name()))
				.sorted(Comparator.comparing(DatabaseScope::name))
				.toList();
		if (includedScopes.isEmpty()) {
			return List.of();
		}

		var tableBuilders = tables(connection, includedScopes);
		columns(connection, includedScopes, tableBuilders);
		primaryKeys(connection, includedScopes, tableBuilders);
		indexes(connection, includedScopes, tableBuilders);

		var tablesByDatabase = new LinkedHashMap<String, List<TableCatalog>>();
		for (var builder : tableBuilders.values()) {
			tablesByDatabase.computeIfAbsent(builder.databaseName, ignored -> new ArrayList<>())
					.add(builder.toTableCatalog());
		}
		var databases = new ArrayList<DatabaseCatalog>();
		for (var scope : includedScopes) {
			var databaseTables = tablesByDatabase.getOrDefault(scope.name(), List.of()).stream()
					.sorted(Comparator.comparing(TableCatalog::name))
					.toList();
			databases.add(new DatabaseCatalog(scope.name(), inferRole(scope.name()), fingerprint(databaseTables), databaseTables));
		}
		return List.copyOf(databases);
	}

	private Map<TableKey, TableBuilder> tables(Connection connection, List<DatabaseScope> scopes) throws SQLException {
		var tables = new LinkedHashMap<TableKey, TableBuilder>();
		var sql = """
				select table_schema, table_name, table_type, table_comment
				     , table_rows
				from information_schema.tables
				where table_schema in (%s)
				  and table_type in ('BASE TABLE', 'VIEW')
				order by table_schema, table_name
				""".formatted(placeholders(scopes.size()));
		try (var statement = prepareScopeStatement(connection, sql, scopes);
				var resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				var databaseName = resultSet.getString("table_schema");
				var tableName = resultSet.getString("table_name");
				tables.put(
						new TableKey(databaseName, tableName),
						new TableBuilder(
								databaseName,
								tableName,
								resultSet.getString("table_type"),
								resultSet.getString("table_comment"),
								estimatedRows(resultSet.getObject("table_rows"))));
			}
		}
		return tables;
	}

	private long estimatedRows(Object tableRows) {
		return tableRows instanceof Number number ? number.longValue() : -1;
	}

	private void columns(Connection connection, List<DatabaseScope> scopes, Map<TableKey, TableBuilder> tables) throws SQLException {
		var sql = """
				select table_schema, table_name, column_name, column_type, character_maximum_length,
				       numeric_precision, is_nullable, column_comment
				from information_schema.columns
				where table_schema in (%s)
				order by table_schema, table_name, ordinal_position
				""".formatted(placeholders(scopes.size()));
		try (var statement = prepareScopeStatement(connection, sql, scopes);
				var resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				var builder = tables.get(new TableKey(resultSet.getString("table_schema"), resultSet.getString("table_name")));
				if (builder == null) {
					continue;
				}
				var columnName = resultSet.getString("column_name");
				builder.columns.add(new ColumnCatalog(
						columnName,
						resultSet.getString("column_type"),
						columnSize(resultSet.getObject("character_maximum_length"), resultSet.getObject("numeric_precision")),
						"YES".equalsIgnoreCase(resultSet.getString("is_nullable")),
						resultSet.getString("column_comment"),
						isScopeCandidate(columnName)));
			}
		}
	}

	private int columnSize(Object characterMaximumLength, Object numericPrecision) {
		var value = characterMaximumLength == null ? numericPrecision : characterMaximumLength;
		return value instanceof Number number ? number.intValue() : 0;
	}

	private void primaryKeys(Connection connection, List<DatabaseScope> scopes, Map<TableKey, TableBuilder> tables) throws SQLException {
		var sql = """
				select table_schema, table_name, column_name
				from information_schema.key_column_usage
				where table_schema in (%s)
				  and constraint_name = 'PRIMARY'
				order by table_schema, table_name, ordinal_position
				""".formatted(placeholders(scopes.size()));
		try (var statement = prepareScopeStatement(connection, sql, scopes);
				var resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				var builder = tables.get(new TableKey(resultSet.getString("table_schema"), resultSet.getString("table_name")));
				if (builder != null) {
					builder.primaryKeys.add(resultSet.getString("column_name"));
				}
			}
		}
	}

	private void indexes(Connection connection, List<DatabaseScope> scopes, Map<TableKey, TableBuilder> tables) throws SQLException {
		var sql = """
				select distinct table_schema, table_name, index_name, column_name, seq_in_index
				from information_schema.statistics
				where table_schema in (%s)
				order by table_schema, table_name, index_name, seq_in_index
				""".formatted(placeholders(scopes.size()));
		try (var statement = prepareScopeStatement(connection, sql, scopes);
				var resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				var builder = tables.get(new TableKey(resultSet.getString("table_schema"), resultSet.getString("table_name")));
				if (builder != null) {
					builder.addIndex(resultSet.getString("index_name"), resultSet.getString("column_name"));
				}
			}
		}
	}

	private PreparedStatement prepareScopeStatement(Connection connection, String sql, List<DatabaseScope> scopes) throws SQLException {
		var statement = connection.prepareStatement(sql);
		for (var index = 0; index < scopes.size(); index++) {
			statement.setString(index + 1, scopes.get(index).name());
		}
		return statement;
	}

	private String placeholders(int size) {
		return String.join(", ", java.util.Collections.nCopies(size, "?"));
	}

	private boolean isScopeCandidate(String columnName) {
		return switch (columnName.toLowerCase()) {
			case "domain", "domain_id", "tenant_id", "tenant", "group_id" -> true;
			default -> false;
		};
	}

	private String inferRole(String databaseName) {
		return databaseName.toLowerCase().contains("common") ? "shared" : "scope_candidate";
	}

	private String fingerprint(List<TableCatalog> tables) {
		return Integer.toHexString(tables.stream().map(table -> table.name() + table.columns().size()).toList().hashCode());
	}

	private record TableKey(String databaseName, String tableName) {
	}

	private static final class TableBuilder {

		private final String databaseName;
		private final String tableName;
		private final String tableType;
		private final String remarks;
		private final long estimatedRows;
		private final List<ColumnCatalog> columns = new ArrayList<>();
		private final List<String> primaryKeys = new ArrayList<>();
		private final List<String> indexes = new ArrayList<>();
		private final List<String> usableIndexColumns = new ArrayList<>();

		private TableBuilder(String databaseName, String tableName, String tableType, String remarks, long estimatedRows) {
			this.databaseName = databaseName;
			this.tableName = tableName;
			this.tableType = tableType;
			this.remarks = remarks;
			this.estimatedRows = estimatedRows;
		}

		private void addIndex(String indexName, String columnName) {
			if (indexName != null && !indexes.contains(indexName)) {
				indexes.add(indexName);
			}
			if (columnName != null && !usableIndexColumns.contains(columnName)) {
				usableIndexColumns.add(columnName);
			}
		}

		private TableCatalog toTableCatalog() {
			return new TableCatalog(
					databaseName,
					tableName,
					tableType,
					remarks,
					estimatedRows,
					List.copyOf(columns),
					List.copyOf(primaryKeys),
					List.copyOf(indexes),
					null,
					null,
					"information_schema.tables.table_rows",
					false,
					List.copyOf(usableIndexColumns));
		}
	}
}
