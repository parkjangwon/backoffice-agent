package org.parkjw.agent.backoffice.catalog;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class JdbcCatalogScanner {

	private final ScanPatternMatcher matcher;

	public JdbcCatalogScanner(ScanPatternMatcher matcher) {
		this.matcher = matcher;
	}

	public List<DatabaseCatalog> scan(DatabaseMetaData metaData, List<DatabaseScope> scopes) throws SQLException {
		var databases = new ArrayList<DatabaseCatalog>();
		for (var scope : scopes) {
			if (matcher.shouldScan(scope.name())) {
				var tables = tables(metaData, scope);
				databases.add(new DatabaseCatalog(scope.name(), inferRole(scope.name()), fingerprint(tables), tables));
			}
		}
		databases.sort(Comparator.comparing(DatabaseCatalog::name));
		return List.copyOf(databases);
	}

	private List<TableCatalog> tables(DatabaseMetaData metaData, DatabaseScope scope) throws SQLException {
		var tables = new ArrayList<TableCatalog>();
		try (var resultSet = metaData.getTables(scope.catalog(), scope.schema(), "%", new String[] {"TABLE", "VIEW"})) {
			while (resultSet.next()) {
				var tableName = resultSet.getString("TABLE_NAME");
				var indexMetadata = indexes(metaData, scope, tableName);
				tables.add(new TableCatalog(
						scope.name(),
						tableName,
						resultSet.getString("TABLE_TYPE"),
						resultSet.getString("REMARKS"),
						-1L,
						columns(metaData, scope, tableName),
						primaryKeys(metaData, scope, tableName),
						indexMetadata.names(),
						null,
						null,
						"unavailable",
						false,
						indexMetadata.columns()));
			}
		}
		tables.sort(Comparator.comparing(TableCatalog::name));
		return List.copyOf(tables);
	}

	private List<ColumnCatalog> columns(DatabaseMetaData metaData, DatabaseScope scope, String tableName) throws SQLException {
		var columns = new ArrayList<ColumnCatalog>();
		try (var resultSet = metaData.getColumns(scope.catalog(), scope.schema(), tableName, "%")) {
			while (resultSet.next()) {
				var name = resultSet.getString("COLUMN_NAME");
				columns.add(new ColumnCatalog(
						name,
						resultSet.getString("TYPE_NAME"),
						resultSet.getInt("COLUMN_SIZE"),
						resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
						resultSet.getString("REMARKS"),
						isScopeCandidate(name)));
			}
		}
		return List.copyOf(columns);
	}

	private List<String> primaryKeys(DatabaseMetaData metaData, DatabaseScope scope, String tableName) throws SQLException {
		var keys = new ArrayList<String>();
		try (var resultSet = metaData.getPrimaryKeys(scope.catalog(), scope.schema(), tableName)) {
			while (resultSet.next()) {
				keys.add(resultSet.getString("COLUMN_NAME"));
			}
		}
		return List.copyOf(keys);
	}

	private IndexMetadata indexes(DatabaseMetaData metaData, DatabaseScope scope, String tableName) throws SQLException {
		var indexes = new ArrayList<String>();
		var columns = new ArrayList<String>();
		try (var resultSet = metaData.getIndexInfo(scope.catalog(), scope.schema(), tableName, false, true)) {
			while (resultSet.next()) {
				var indexName = resultSet.getString("INDEX_NAME");
				if (indexName != null && !indexes.contains(indexName)) {
					indexes.add(indexName);
				}
				var columnName = resultSet.getString("COLUMN_NAME");
				if (columnName != null && !columns.contains(columnName)) {
					columns.add(columnName);
				}
			}
		}
		return new IndexMetadata(List.copyOf(indexes), List.copyOf(columns));
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

	private record IndexMetadata(List<String> names, List<String> columns) {
	}
}
