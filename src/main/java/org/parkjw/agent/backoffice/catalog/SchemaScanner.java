package org.parkjw.agent.backoffice.catalog;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

@Service
public class SchemaScanner {

	private final DataSource dataSource;
	private final DatabaseDiscovery databaseDiscovery;
	private final ScopeMappingDiscoverer scopeMappingDiscoverer;
	private final MySqlCatalogScanner mySqlCatalogScanner;
	private final JdbcCatalogScanner jdbcCatalogScanner;
	private final ScanPatternMatcher scanPatternMatcher;

	public SchemaScanner(
			DataSource dataSource,
			DatabaseDiscovery databaseDiscovery,
			ScopeMappingDiscoverer scopeMappingDiscoverer,
			MySqlCatalogScanner mySqlCatalogScanner,
			JdbcCatalogScanner jdbcCatalogScanner,
			ScanPatternMatcher scanPatternMatcher) {
		this.dataSource = dataSource;
		this.databaseDiscovery = databaseDiscovery;
		this.scopeMappingDiscoverer = scopeMappingDiscoverer;
		this.mySqlCatalogScanner = mySqlCatalogScanner;
		this.jdbcCatalogScanner = jdbcCatalogScanner;
		this.scanPatternMatcher = scanPatternMatcher;
	}

	public CatalogSnapshot scan() {
		return scan(null);
	}

	public CatalogSnapshot scan(CatalogScanRequest request) {
		var startedAt = Instant.now();
		var startedNanos = System.nanoTime();
		var connection = DataSourceUtils.getConnection(dataSource);
		try {
			connection.setReadOnly(true);
			var metaData = connection.getMetaData();
			var dialect = DatabaseDialect.fromProductName(metaData.getDatabaseProductName());
			var scan = scanDatabases(connection, metaData, request);
			var scopeMappings = scopeMappingDiscoverer.discover(connection, scan.databases());
			return new CatalogSnapshot(
					startedAt,
					"backoffice-agent",
					dialect.name(),
					metaData.getDatabaseProductVersion(),
					scan.databases(),
					scopeMappings,
					CatalogScanMetadata.fromScan(Duration.ofNanos(System.nanoTime() - startedNanos), scan.discoveredScopes(), scan.databases()));
		}
		catch (SQLException exception) {
			throw new SchemaScanException("Failed to scan database metadata", exception);
		}
		finally {
			DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}

	private DatabaseScan scanDatabases(Connection connection, DatabaseMetaData metaData, CatalogScanRequest request) throws SQLException {
		var discoveredScopes = databaseDiscovery.discover(connection, metaData);
		var selectedScopes = discoveredScopes.stream()
				.filter(scope -> scanPatternMatcher.shouldScan(scope.name(), request))
				.toList();
		var databases = databaseDiscovery.supportsShowDatabases(metaData)
				? mySqlCatalogScanner.scan(connection, selectedScopes)
				: jdbcCatalogScanner.scan(metaData, selectedScopes);
		return new DatabaseScan(discoveredScopes, databases);
	}

	private record DatabaseScan(List<DatabaseScope> discoveredScopes, List<DatabaseCatalog> databases) {
		public DatabaseScan {
			discoveredScopes = discoveredScopes == null ? List.of() : List.copyOf(discoveredScopes);
			databases = databases == null ? List.of() : List.copyOf(databases);
		}
	}

}
