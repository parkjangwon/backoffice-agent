package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class NaturalLanguageResultFormatter {

	private static final int PREVIEW_ROWS = 5;

	private final QueryResultViewMapper viewMapper;
	private final NoResultGuidanceService noResultGuidanceService;

	public NaturalLanguageResultFormatter(QueryResultViewMapper viewMapper, NoResultGuidanceService noResultGuidanceService) {
		this.viewMapper = viewMapper;
		this.noResultGuidanceService = noResultGuidanceService;
	}

	public String format(QueryResult result) {
		if (result.rowCount() == 0) {
			return noResultGuidanceService.guidanceSentence();
		}
		var columns = viewMapper.visibleColumns(result);
		var preview = result.rows().stream()
				.limit(PREVIEW_ROWS)
				.map(row -> formatRow(row, columns, result.displayNames()))
				.collect(Collectors.joining(System.lineSeparator()));
		return """
				조회 결과는 총 %d건입니다.
				주요 결과:
				%s
				""".formatted(result.rowCount(), preview).trim();
	}

	private String formatRow(Map<String, Object> row, List<String> columns, Map<String, String> displayNames) {
		if (columns.size() == 1) {
			return "- " + String.valueOf(viewMapper.displayValue(row, columns.getFirst()));
		}
		return "- " + columns.stream()
				.map(column -> viewMapper.label(column, displayNames) + ": " + String.valueOf(viewMapper.displayValue(row, column)))
				.collect(Collectors.joining(", "));
	}
}
