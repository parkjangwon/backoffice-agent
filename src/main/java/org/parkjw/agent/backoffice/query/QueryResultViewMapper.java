package org.parkjw.agent.backoffice.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class QueryResultViewMapper {

	private final NoResultGuidanceService noResultGuidanceService;

	public QueryResultViewMapper(NoResultGuidanceService noResultGuidanceService) {
		this.noResultGuidanceService = noResultGuidanceService;
	}

	public UserQueryResponse toResponse(QueryResult result) {
		if (result.rowCount() == 0) {
			var noResult = noResultGuidanceService.guidance();
			return new UserQueryResponse(0, List.of(), List.of(), noResult.message(), noResult.guidance());
		}
		var columns = visibleColumns(result);
		var labels = columns.stream()
				.map(column -> label(column, result.displayNames()))
				.toList();
		var rows = result.rows().stream()
				.map(row -> toDisplayRow(row, columns, result.displayNames()))
				.toList();
		return new UserQueryResponse(result.rowCount(), labels, rows, "", "");
	}

	public List<String> visibleColumns(QueryResult result) {
		var columns = result.columns().stream()
				.filter(column -> !column.endsWith("Text"))
				.toList();
		var withoutScopeColumns = columns.stream()
				.filter(column -> !isScopeColumn(column))
				.toList();
		if (!withoutScopeColumns.isEmpty()) {
			return withoutScopeColumns;
		}
		return columns;
	}

	public String label(String column, Map<String, String> displayNames) {
		var displayName = cleanDisplayName(displayNames.get(column), column);
		if (displayName != null) {
			return displayName;
		}
		var normalized = normalize(column);
		if (containsAny(normalized, "subject", "title")) {
			return "메일 제목";
		}
		if (containsAny(normalized, "email", "emailid", "mailaddress")) {
			return "이메일 주소";
		}
		if (containsAny(normalized, "domain")) {
			return "도메인";
		}
		if (containsAny(normalized, "mailusedsize", "usedbytes", "usedsize")) {
			return "사용량";
		}
		if (containsAny(normalized, "mailtotalsize", "quotasize", "totalsize", "capacity")) {
			return "할당량";
		}
		if (containsAny(normalized, "count", "cnt", "totalrow")) {
			return "건수";
		}
		if (containsAny(normalized, "datetime", "timestamp", "timemillis", "created", "updated", "sent", "received")) {
			return "일시";
		}
		return column;
	}

	public Object displayValue(Map<String, Object> row, String column) {
		var formatted = row.get(column + "Text");
		return formatted == null ? row.get(column) : formatted;
	}

	private Map<String, Object> toDisplayRow(
			Map<String, Object> row,
			List<String> columns,
			Map<String, String> displayNames) {
		return columns.stream()
				.collect(Collectors.toMap(
						column -> label(column, displayNames),
						column -> displayValue(row, column),
						(left, right) -> right,
						LinkedHashMap::new));
	}

	private boolean isScopeColumn(String column) {
		return "domain".equalsIgnoreCase(column);
	}

	private String cleanDisplayName(String displayName, String column) {
		if (displayName == null || displayName.isBlank() || normalize(displayName).equals(normalize(column))) {
			return null;
		}
		return displayName.strip()
				.replaceAll("(?i)\\s*(byte|bytes|kb|mb|gb|milliseconds|seconds|count|percent|%)\\s*$", "")
				.strip();
	}

	private boolean containsAny(String value, String... keywords) {
		for (var keyword : keywords) {
			if (value.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT)
				.replace("-", "")
				.replace("_", "")
				.replace(" ", "");
	}
}
