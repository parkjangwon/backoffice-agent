package org.parkjw.agent.backoffice.catalog;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatabaseDiscovery {

	public List<DatabaseScope> discover(Connection connection, DatabaseMetaData metaData) throws SQLException {
		if (!supportsShowDatabases(metaData)) {
			return jdbcSchemaScopes(connection, metaData);
		}
		try (var resultSet = connection.createStatement().executeQuery("show databases")) {
			var scopes = new ArrayList<DatabaseScope>();
			while (resultSet.next()) {
				var databaseName = resultSet.getString(1);
				scopes.add(new DatabaseScope(databaseName, null, databaseName));
			}
			return List.copyOf(scopes);
		}
		catch (SQLException exception) {
			log.debug("SHOW DATABASES failed. Falling back to JDBC schema metadata. reason={}", exception.getMessage());
			return jdbcSchemaScopes(connection, metaData);
		}
	}

	public boolean supportsShowDatabases(DatabaseMetaData metaData) throws SQLException {
		var productName = metaData.getDatabaseProductName().toLowerCase();
		return productName.contains("mysql") || productName.contains("mariadb");
	}

	private List<DatabaseScope> jdbcSchemaScopes(Connection connection, DatabaseMetaData metaData) throws SQLException {
		var catalog = connection.getCatalog();
		var scopes = new ArrayList<DatabaseScope>();
		try (var schemas = metaData.getSchemas()) {
			while (schemas.next()) {
				var schema = schemas.getString("TABLE_SCHEM");
				scopes.add(new DatabaseScope(catalog, schema, schema));
			}
		}
		return scopes.stream().distinct().toList();
	}
}
