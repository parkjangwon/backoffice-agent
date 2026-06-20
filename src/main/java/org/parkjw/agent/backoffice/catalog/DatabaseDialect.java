package org.parkjw.agent.backoffice.catalog;

import java.util.List;
import java.util.Locale;

public enum DatabaseDialect {
	MYSQL("`", "`", "limit %d", List.of("information_schema", "mysql", "performance_schema", "sys")),
	MARIADB("`", "`", "limit %d", List.of("information_schema", "mysql", "performance_schema", "sys")),
	POSTGRESQL("\"", "\"", "limit %d", List.of("information_schema", "pg_catalog", "pg_toast")),
	SQLITE("\"", "\"", "limit %d", List.of("sqlite_master", "sqlite_temp_master")),
	ORACLE("\"", "\"", "fetch first %d rows only", List.of("SYS", "SYSTEM", "XDB", "CTXSYS", "MDSYS"));

	private final String openQuote;
	private final String closeQuote;
	private final String limitTemplate;
	private final List<String> systemSchemas;

	DatabaseDialect(String openQuote, String closeQuote, String limitTemplate, List<String> systemSchemas) {
		this.openQuote = openQuote;
		this.closeQuote = closeQuote;
		this.limitTemplate = limitTemplate;
		this.systemSchemas = List.copyOf(systemSchemas);
	}

	public String quoteIdentifier(String identifier) {
		var quote = openQuote;
		return openQuote + identifier.replace(quote, quote + quote) + closeQuote;
	}

	public String limitClause(int limit) {
		return limitTemplate.formatted(Math.max(1, limit));
	}

	public List<String> systemSchemas() {
		return systemSchemas;
	}

	public static DatabaseDialect fromProductName(String productName) {
		var normalized = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
		if (normalized.contains("mariadb")) {
			return MARIADB;
		}
		if (normalized.contains("mysql")) {
			return MYSQL;
		}
		if (normalized.contains("postgres")) {
			return POSTGRESQL;
		}
		if (normalized.contains("sqlite")) {
			return SQLITE;
		}
		if (normalized.contains("oracle")) {
			return ORACLE;
		}
		return MYSQL;
	}
}
