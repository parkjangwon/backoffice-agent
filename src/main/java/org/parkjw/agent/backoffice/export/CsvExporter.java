package org.parkjw.agent.backoffice.export;

import java.io.IOException;
import java.io.StringWriter;

import org.parkjw.agent.backoffice.query.NoResultGuidanceService;
import org.parkjw.agent.backoffice.query.QueryResult;
import org.parkjw.agent.backoffice.query.QueryResultViewMapper;

import org.apache.commons.csv.CSVFormat;
import org.springframework.stereotype.Component;

@Component
public class CsvExporter {

	private final QueryResultViewMapper viewMapper;
	private final NoResultGuidanceService noResultGuidanceService;

	public CsvExporter(QueryResultViewMapper viewMapper, NoResultGuidanceService noResultGuidanceService) {
		this.viewMapper = viewMapper;
		this.noResultGuidanceService = noResultGuidanceService;
	}

	public String export(QueryResult result) {
		if (result.rowCount() == 0) {
			return exportNoResult(result);
		}
		var columns = viewMapper.visibleColumns(result);
		var headers = columns.stream()
				.map(column -> viewMapper.label(column, result.displayNames()))
				.toArray(String[]::new);
		try (var writer = new StringWriter();
				var printer = CSVFormat.DEFAULT.builder()
						.setHeader(headers)
						.get()
						.print(writer)) {
			for (var row : result.rows()) {
				var values = columns.stream()
						.map(column -> safeCell(viewMapper.displayValue(row, column)))
						.toList();
				printer.printRecord(values);
			}
			return writer.toString();
		}
		catch (IOException exception) {
			throw new CsvExportException("Failed to export CSV.", exception);
		}
	}

	private String exportNoResult(QueryResult result) {
		try (var writer = new StringWriter();
				var printer = CSVFormat.DEFAULT.builder()
						.setHeader("안내")
						.get()
						.print(writer)) {
			printer.printRecord(noResultGuidanceService.guidanceSentence());
			return writer.toString();
		}
		catch (IOException exception) {
			throw new CsvExportException("Failed to export CSV.", exception);
		}
	}

	private Object safeCell(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String text && startsWithFormulaPrefix(text)) {
			return "'" + text;
		}
		if (value instanceof java.util.List<?> list) {
			return list.toString();
		}
		return value;
	}

	private boolean startsWithFormulaPrefix(String text) {
		return !text.isBlank() && switch (text.charAt(0)) {
			case '=', '+', '-', '@' -> true;
			default -> false;
		};
	}
}
