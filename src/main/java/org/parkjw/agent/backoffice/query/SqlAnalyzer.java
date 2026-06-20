package org.parkjw.agent.backoffice.query;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import org.springframework.stereotype.Component;

@Component
public class SqlAnalyzer {

	public ParsedSql requireSingleSelect(String sql) {
		var normalized = stripTrailingSemicolon(sql);
		try {
			var statements = CCJSqlParserUtil.parseStatements(normalized);
			if (statements.size() != 1 || !(statements.getFirst() instanceof Select select)) {
				throw new SqlPolicyException("Only a single SELECT statement is allowed.");
			}
			return new ParsedSql(normalized, select, tableReferences(select));
		}
		catch (JSQLParserException exception) {
			throw new SqlPolicyException("SQL could not be parsed safely.", exception);
		}
	}

	public Set<String> tableReferences(String sql) {
		return requireSingleSelect(sql).tableReferences();
	}

	private Set<String> tableReferences(Statement statement) {
		return new TablesNamesFinder<>()
				.getTables(statement)
				.stream()
				.map(SqlAnalyzer::normalizeTableReference)
				.collect(Collectors.toCollection(java.util.LinkedHashSet::new));
	}

	private static String normalizeTableReference(String reference) {
		return reference.replace("`", "")
				.replace("\"", "")
				.replaceAll("\\s+", "")
				.toLowerCase(Locale.ROOT);
	}

	private String stripTrailingSemicolon(String sql) {
		var trimmed = sql.trim();
		while (trimmed.endsWith(";")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
		}
		return trimmed;
	}

	public record ParsedSql(String sql, Select select, Set<String> tableReferences) {
	}
}
