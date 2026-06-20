package org.parkjw.agent.backoffice.query;

import java.util.regex.Pattern;

final class SqlIdentifierQuoteNormalizer {

	private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_$]*";
	private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile("\"(" + IDENTIFIER + ")\"\\s*\\.\\s*\"(" + IDENTIFIER + ")\"");
	private static final Pattern TABLE_IDENTIFIER = Pattern.compile("(?i)\\b(from|join)\\s+\"(" + IDENTIFIER + ")\"");
	private static final Pattern ALIAS_IDENTIFIER = Pattern.compile("(?i)\\b(as)\\s+\"(" + IDENTIFIER + ")\"");

	private SqlIdentifierQuoteNormalizer() {
	}

	static String normalize(String sql) {
		var normalized = QUALIFIED_IDENTIFIER.matcher(sql).replaceAll("`$1`.`$2`");
		normalized = TABLE_IDENTIFIER.matcher(normalized).replaceAll("$1 `$2`");
		return ALIAS_IDENTIFIER.matcher(normalized).replaceAll("$1 `$2`");
	}
}
