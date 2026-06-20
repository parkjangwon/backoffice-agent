package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.Locale;

public class SqlSafetyValidatorTestHelper {

	private static final List<String> BLOCKED_TOKENS = List.of(
			" into outfile", " into dumpfile", " load_file(", " for update", " lock in share mode",
			" procedure analyse", "/*! ", "--", "/*", "*/");

	public static void validate(String sql) {
		if (sql == null) {
			return;
		}
		var lower = " " + sql.toLowerCase(Locale.ROOT);
		for (var token : BLOCKED_TOKENS) {
			if (lower.contains(token)) {
				throw new SqlPolicyException("Read-only query contains a blocked token: " + token.trim());
			}
		}
		
		var trimmed = sql.trim();
		while (trimmed.endsWith(";")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
		}
		if (!trimmed.toLowerCase(Locale.ROOT).startsWith("select")) {
			throw new SqlPolicyException("Only a single SELECT statement is allowed.");
		}
	}
}
