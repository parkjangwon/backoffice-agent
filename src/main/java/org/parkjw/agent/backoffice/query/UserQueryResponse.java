package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.Map;

public record UserQueryResponse(
		int rowCount,
		List<String> columns,
		List<Map<String, Object>> rows,
		String message,
		String guidance) {

	public UserQueryResponse {
		columns = columns == null ? List.of() : List.copyOf(columns);
		rows = rows == null ? List.of() : List.copyOf(rows);
		message = message == null ? "" : message;
		guidance = guidance == null ? "" : guidance;
	}
}
