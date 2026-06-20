package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class SqlSafetyValidator {

	private static final List<String> BLOCKED_TOKENS = List.of(
			" into outfile", " into dumpfile", " load_file(", " for update", " lock in share mode",
			" procedure analyse", "/*! ", "--", "/*", "*/");

	private final SqlAnalyzer analyzer;

	public SqlSafetyValidator(SqlAnalyzer analyzer) {
		this.analyzer = analyzer;
	}

	public String requireReadOnlySelect(String sql) {
		var parsed = analyzer.requireSingleSelect(sql);
		rejectBlockedTokens(parsed.sql());
		return parsed.sql();
	}

	private void rejectBlockedTokens(String sql) {
		var lower = " " + sql.toLowerCase(Locale.ROOT);
		for (var token : BLOCKED_TOKENS) {
			if (lower.contains(token)) {
				throw new SqlPolicyException("Read-only query contains a blocked token: " + token.trim());
			}
		}
	}

}
