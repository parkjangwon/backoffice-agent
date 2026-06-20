package org.parkjw.agent.backoffice.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.ColumnCatalog;
import org.parkjw.agent.backoffice.catalog.ColumnSemanticResolver;

import org.springframework.stereotype.Service;

@Service
public class ResultPresentationService {

	private final HumanReadableValueFormatter formatter;

	public ResultPresentationService(HumanReadableValueFormatter formatter) {
		this.formatter = formatter;
	}

	public QueryResult present(QueryResult result, CatalogSnapshot catalog) {
		if (result.rows().isEmpty() || result.columns().isEmpty()) {
			return result;
		}
		var metadataByColumn = metadataByColumn(catalog);
		var columns = new ArrayList<>(result.columns());
		var displayNames = new LinkedHashMap<>(result.displayNames());
		var rows = result.rows().stream()
				.map(row -> presentRow(row, result.columns(), columns, displayNames, metadataByColumn))
				.toList();
		return new QueryResult(result.sql(), java.util.List.copyOf(columns), rows, result.rowCount(), displayNames);
	}

	private Map<String, ColumnCatalog> metadataByColumn(CatalogSnapshot catalog) {
		return catalog.databases().stream()
				.flatMap(database -> database.tables().stream())
				.flatMap(table -> table.columns().stream())
				.collect(Collectors.toMap(
						column -> key(column.name()),
						Function.identity(),
						this::chooseRicherMetadata,
						LinkedHashMap::new));
	}

	private Map<String, Object> presentRow(
			Map<String, Object> row,
			java.util.List<String> originalColumns,
			ArrayList<String> outputColumns,
			Map<String, String> displayNames,
			Map<String, ColumnCatalog> metadataByColumn) {
		var output = new LinkedHashMap<String, Object>(row);
		for (var column : originalColumns) {
			if (column.endsWith("Text")) {
				continue;
			}
			var metadata = metadataByColumn.getOrDefault(key(column), inferredColumn(column));
			displayNames.putIfAbsent(column, metadata.displayName());
			var formatted = formatter.format(row.get(column), metadata.semanticType(), metadata.unit());
			if (formatted == null) {
				continue;
			}
			var textColumn = column + "Text";
			output.put(textColumn, formatted);
			displayNames.putIfAbsent(textColumn, metadata.displayName());
			if (!outputColumns.contains(textColumn)) {
				outputColumns.add(textColumn);
			}
		}
		return output;
	}

	private ColumnCatalog inferredColumn(String column) {
		var semanticType = ColumnSemanticResolver.semanticType(column, "", "");
		return new ColumnCatalog(
				column,
				"",
				0,
				true,
				null,
				false,
				semanticType,
				ColumnSemanticResolver.unit(column, "", "", semanticType),
				ColumnSemanticResolver.displayName(column, null));
	}

	private ColumnCatalog chooseRicherMetadata(ColumnCatalog left, ColumnCatalog right) {
		if (left.remarks() != null && !left.remarks().isBlank()) {
			return left;
		}
		return right;
	}

	private String key(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
