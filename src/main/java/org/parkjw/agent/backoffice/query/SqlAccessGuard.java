package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.config.AiQueryProperties;
import org.parkjw.agent.backoffice.security.AccessContext;

import org.springframework.stereotype.Component;

@Component
public class SqlAccessGuard {

	private static final List<String> BLOCKED_SYSTEM_OBJECTS = List.of(
			"information_schema", "performance_schema", "mysql.", "sys.");

	private static final List<String> BLOCKED_SECRET_COLUMNS = List.of(
			" password", ".password", " pw", ".pw", "token", "secret", "client_secret", "authorization_code",
			"access_token", "refresh_token", "device_code", "authenticationkey", "authkey", "apikey", "api_key",
			"passphrase", "sessionkey");

	private static final List<String> BLOCKED_FUNCTIONS = List.of(
			" load_file(", " sleep(", " benchmark(", " current_user(", " user(", " database(", " version(",
			" @@version", " @@hostname", " @@datadir");

	private final AiQueryProperties properties;
	private final SqlAnalyzer analyzer;

	public SqlAccessGuard(AiQueryProperties properties, SqlAnalyzer analyzer) {
		this.properties = properties;
		this.analyzer = analyzer;
	}

	private static final List<String> SYSTEM_STRUCTURE_TABLE_PATTERNS = List.of(
			"policy", "config", "apikey", "oauth", "sso", "session", "task", "linkage",
			"insa", "password", "pw_", "secure", "auth", "token", "secret");

	public void inspect(String sql, AccessContext accessContext, CatalogSnapshot catalog) {
		var lower = " " + sql.toLowerCase(Locale.ROOT);
		rejectSystemObjects(lower);
		rejectSecretColumns(lower);
		rejectEncryptedNameColumns(lower);
		rejectBlockedFunctions(lower);
		rejectNonServiceUsageTables(sql, catalog);
		rejectOutOfScopeExternalValues(lower, accessContext);
	}

	private void rejectSystemObjects(String sql) {
		for (var object : BLOCKED_SYSTEM_OBJECTS) {
			if (sql.contains(object)) {
				throw new SqlPolicyException("SQL attempts to query blocked system or sensitive object: " + object);
			}
		}
	}

	private void rejectSecretColumns(String sql) {
		for (var column : BLOCKED_SECRET_COLUMNS) {
			if (sql.contains(column)) {
				throw new SqlPolicyException("SQL attempts to query blocked secret credential data.");
			}
		}
	}

	private void rejectEncryptedNameColumns(String sql) {
		if (!properties.dataPolicy().userNameEncrypted()) {
			return;
		}
		var pattern = Pattern.compile("(^|[\\s,`.])(?:name|username|user_name|username|emp_name_kr)([\\s,`.]|$)", Pattern.CASE_INSENSITIVE);
		if (pattern.matcher(sql).find()) {
			throw new SqlPolicyException("SQL attempts to query encrypted user-name data. Use email as user identifier.");
		}
	}

	private void rejectBlockedFunctions(String sql) {
		for (var function : BLOCKED_FUNCTIONS) {
			if (sql.contains(function)) {
				throw new SqlPolicyException("SQL attempts to use blocked system function or variable.");
			}
		}
	}

	private void rejectNonServiceUsageTables(String sql, CatalogSnapshot catalog) {
		var tableReferences = analyzer.tableReferences(sql);
		for (var database : catalog.databases()) {
			for (var table : database.tables()) {
				var tableReference = database.name().toLowerCase(Locale.ROOT) + "." + table.name().toLowerCase(Locale.ROOT);
				if (tableReferences.contains(tableReference) && systemStructureTable(table)) {
					throw new SqlPolicyException("SQL references non-service-usage table: " + table.name());
				}
			}
		}
	}

	private boolean systemStructureTable(TableCatalog table) {
		var tableName = table.name().toLowerCase(Locale.ROOT);
		if (SYSTEM_STRUCTURE_TABLE_PATTERNS.stream().anyMatch(tableName::contains)) {
			return true;
		}
		var sensitiveColumns = table.columns().stream()
				.filter(column -> sensitiveColumnName(column.name()))
				.count();
		return sensitiveColumns > 0 && sensitiveColumns * 2 >= Math.max(1, table.columns().size());
	}

	private boolean sensitiveColumnName(String columnName) {
		var normalized = columnName.toLowerCase(Locale.ROOT).replace("_", "");
		return normalized.contains("password")
				|| normalized.equals("pw")
				|| normalized.contains("token")
				|| normalized.contains("secret")
				|| normalized.contains("auth")
				|| normalized.contains("apikey")
				|| normalized.contains("credential");
	}

	private void rejectOutOfScopeExternalValues(String sql, AccessContext accessContext) {
		var matcher = Pattern.compile("'([a-z0-9][a-z0-9.-]*\\.[a-z]{2,})'").matcher(sql);
		while (matcher.find()) {
			var scopeValue = matcher.group(1);
			if (!accessContext.globalAccess()
					&& accessContext.scopeValues().stream().noneMatch(allowed -> allowed.equalsIgnoreCase(scopeValue))) {
				throw new SqlPolicyException("SQL references a value outside the access scope: " + scopeValue);
			}
		}
	}
}
