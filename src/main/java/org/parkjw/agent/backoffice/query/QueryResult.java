package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.Map;

public record QueryResult(
		String sql,
		List<String> columns,
		List<Map<String, Object>> rows,
		int rowCount,
		Map<String, String> displayNames) {

	public QueryResult(String sql, List<String> columns, List<Map<String, Object>> rows, int rowCount) {
		this(sql, columns, rows, rowCount, Map.of());
	}

	public QueryResult {
		columns = columns == null ? List.of() : List.copyOf(columns);
		rows = rows == null ? List.of() : List.copyOf(rows);
		displayNames = displayNames == null ? Map.of() : Map.copyOf(displayNames);
	}
}
