package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.regex.Pattern;

final class SqlIdentifierQuoteNormalizer {

	private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_$]*";
	private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile("\"(" + IDENTIFIER + ")\"\\s*\\.\\s*\"(" + IDENTIFIER + ")\"");
	private static final Pattern TABLE_IDENTIFIER = Pattern.compile("(?i)\\b(from|join)\\s+\"(" + IDENTIFIER + ")\"");
	private static final Pattern ALIAS_IDENTIFIER = Pattern.compile("(?i)\\b(as)\\s+\"(" + IDENTIFIER + ")\"");
	private static final Pattern QUOTED_PLACEHOLDER_DATABASE = Pattern.compile("(?i)`(?:database|db|schema)`\\s*\\.\\s*`(" + IDENTIFIER + ")`");
	private static final Pattern PLAIN_PLACEHOLDER_DATABASE = Pattern.compile("(?i)\\b(?:database|db|schema)\\b\\s*\\.\\s*`?(" + IDENTIFIER + ")`?");

	private SqlIdentifierQuoteNormalizer() {
	}

	static String normalize(String sql) {
		var normalized = QUALIFIED_IDENTIFIER.matcher(sql).replaceAll("`$1`.`$2`");
		normalized = TABLE_IDENTIFIER.matcher(normalized).replaceAll("$1 `$2`");
		return ALIAS_IDENTIFIER.matcher(normalized).replaceAll("$1 `$2`");
	}

	static String normalize(String sql, List<String> allowedDatabases) {
		var normalized = normalize(sql);
		if (allowedDatabases == null || allowedDatabases.size() != 1) {
			return normalized;
		}
		var database = allowedDatabases.get(0);
		normalized = QUOTED_PLACEHOLDER_DATABASE.matcher(normalized)
				.replaceAll(match -> qualified(database, match.group(1)));
		return PLAIN_PLACEHOLDER_DATABASE.matcher(normalized)
				.replaceAll(match -> qualified(database, match.group(1)));
	}

	private static String qualified(String database, String table) {
		return "`" + database.replace("`", "``") + "`.`" + table.replace("`", "``") + "`";
	}
}
