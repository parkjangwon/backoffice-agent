package org.parkjw.agent.backoffice.query;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.security.AccessContext;

import org.springframework.stereotype.Component;

@Component
public class SqlCatalogGroundingValidator {

	private final CatalogScopeResolver scopeResolver;
	private final SqlAnalyzer analyzer;

	public SqlCatalogGroundingValidator(CatalogScopeResolver scopeResolver, SqlAnalyzer analyzer) {
		this.scopeResolver = scopeResolver;
		this.analyzer = analyzer;
	}

	public void requireCatalogTables(String sql, CatalogSnapshot catalog, AccessContext accessContext) {
		var allowedTables = allowedTables(catalog, accessContext);
		for (var reference : analyzer.tableReferences(sql)) {
			if (!reference.contains(".")) {
				throw new SqlPolicyException("SQL table reference must be fully-qualified as database.table: " + reference);
			}
			if (!allowedTables.contains(reference)) {
				throw new SqlPolicyException("SQL references a table outside the allowed catalog scope: " + reference);
			}
		}
	}

	private Set<String> allowedTables(CatalogSnapshot catalog, AccessContext accessContext) {
		var targetDatabases = scopeResolver.targetDatabases(accessContext, catalog);
		var tables = new LinkedHashSet<String>();
		for (var database : catalog.databases()) {
			var databaseName = database.name().toLowerCase(Locale.ROOT);
			if (!targetDatabases.isEmpty() && !targetDatabases.contains(databaseName)) {
				continue;
			}
			for (var table : database.tables()) {
				tables.add(databaseName + "." + table.name().toLowerCase(Locale.ROOT));
			}
		}
		return tables;
	}

}
