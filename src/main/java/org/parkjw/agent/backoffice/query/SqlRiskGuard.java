package org.parkjw.agent.backoffice.query;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.ColumnSemanticType;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.catalog.TableRole;
import org.parkjw.agent.backoffice.security.AccessContext;

import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.SelectItem;

import org.springframework.stereotype.Component;

@Component
public class SqlRiskGuard {

	private static final long LARGE_TABLE_ROWS = 100_000L;
	private static final Set<TableRole> SERVICE_TABLE_ROLES = Set.of(
			TableRole.UNKNOWN,
			TableRole.ACCOUNT,
			TableRole.EVENT,
			TableRole.METRIC,
			TableRole.CONTENT);
	private static final Set<String> AGGREGATE_FUNCTIONS = Set.of("count", "sum", "avg", "min", "max");

	private final SqlAnalyzer analyzer;

	public SqlRiskGuard(SqlAnalyzer analyzer) {
		this.analyzer = analyzer;
	}

	public void inspect(String sql, AccessContext accessContext, BusinessIntent intent, CatalogSnapshot catalog, String action) {
		var parsed = analyzer.requireSingleSelect(sql);
		var plainSelect = parsed.select().getPlainSelect();
		if (plainSelect == null) {
			throw new SqlPolicyException("SQL risk guard requires a plain SELECT.");
		}
		var tables = referencedTables(parsed.tableReferences(), catalog);
		if (tables.isEmpty()) {
			return;
		}
		var shape = new QueryShape(
				selectsStar(plainSelect.getSelectItems()),
				hasAggregate(plainSelect.getSelectItems()),
				plainSelect.getGroupBy() != null,
				normalized(plainSelect.getWhere() == null ? "" : plainSelect.getWhere().toString()),
				filterColumns(tables));
		rejectSelectStarOnBroadServiceTable(shape, tables);
		rejectBroadScopedActorExport(shape, tables, accessContext, action);
		rejectCrossScopeAggregate(shape, tables, accessContext, intent);
	}

	private void rejectSelectStarOnBroadServiceTable(QueryShape shape, List<RiskTable> tables) {
		if (shape.selectsStar() && tables.stream().anyMatch(RiskTable::largeOrUnknownServiceTable)) {
			throw new SqlPolicyException("SQL uses select * over a large or unknown service table.");
		}
	}

	private void rejectBroadScopedActorExport(
			QueryShape shape,
			List<RiskTable> tables,
			AccessContext accessContext,
			String action) {
		if (accessContext.globalAccess() || !"EXPORT".equalsIgnoreCase(action)) {
			return;
		}
		if (tables.stream().anyMatch(RiskTable::largeOrUnknownServiceTable) && !shape.hasEmailDateOrScopeFilter()) {
			throw new SqlPolicyException("SQL risk guard blocked a broad export over a large or unknown service table.");
		}
	}

	private void rejectCrossScopeAggregate(
			QueryShape shape,
			List<RiskTable> tables,
			AccessContext accessContext,
			BusinessIntent intent) {
		if (accessContext.globalAccess() || intent.category() != BusinessIntentCategory.STATISTICS) {
			return;
		}
		if ((shape.aggregate() || shape.grouped())
				&& tables.stream().anyMatch(RiskTable::largeOrUnknownServiceTable)
				&& !shape.hasScopeFilter()) {
			throw new SqlPolicyException("SQL risk guard blocked a cross-tenant aggregate outside scope.");
		}
	}

	private List<RiskTable> referencedTables(Set<String> references, CatalogSnapshot catalog) {
		var catalogTables = catalogTables(catalog);
		var tables = new ArrayList<RiskTable>();
		for (var reference : references) {
			var normalizedReference = normalizedReference(reference);
			var table = catalogTables.get(normalizedReference);
			tables.add(table == null
					? new RiskTable(normalizedReference, null)
					: new RiskTable(normalizedReference, table));
		}
		return List.copyOf(tables);
	}

	private Map<String, TableCatalog> catalogTables(CatalogSnapshot catalog) {
		var tables = new LinkedHashMap<String, TableCatalog>();
		for (var database : catalog.databases()) {
			for (var table : database.tables()) {
				tables.put(normalizedReference(database.name() + "." + table.name()), table);
			}
		}
		return tables;
	}

	private boolean selectsStar(List<SelectItem<?>> selectItems) {
		return selectItems.stream()
				.map(SelectItem::getExpression)
				.anyMatch(expression -> expression instanceof AllColumns);
	}

	private boolean hasAggregate(List<SelectItem<?>> selectItems) {
		return selectItems.stream()
				.map(SelectItem::getExpression)
				.anyMatch(expression -> expression instanceof Function function
						&& AGGREGATE_FUNCTIONS.contains(normalized(function.getName())));
	}

	private FilterColumns filterColumns(List<RiskTable> tables) {
		var columnsByType = new EnumMap<ColumnSemanticType, List<String>>(ColumnSemanticType.class);
		for (var table : tables) {
			if (table.catalog() == null) {
				continue;
			}
			for (var column : table.catalog().columns()) {
				columnsByType.computeIfAbsent(column.semanticType(), ignored -> new ArrayList<>())
						.add(column.name());
			}
		}
		return new FilterColumns(
				columns(columnsByType, ColumnSemanticType.EMAIL),
				columns(columnsByType, ColumnSemanticType.DOMAIN),
				columns(columnsByType, ColumnSemanticType.DATETIME));
	}

	private Set<String> columns(Map<ColumnSemanticType, List<String>> columnsByType, ColumnSemanticType type) {
		return columnsByType.getOrDefault(type, List.of())
				.stream()
				.map(this::normalized)
				.collect(Collectors.toUnmodifiableSet());
	}

	private String normalizedReference(String value) {
		return normalized(value).replace("`", "").replaceAll("\\s+", "");
	}

	private String normalized(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private record RiskTable(String reference, TableCatalog catalog) {

		private boolean largeOrUnknownServiceTable() {
			return serviceTable() && (catalog == null || catalog.estimatedRows() < 0 || catalog.estimatedRows() >= LARGE_TABLE_ROWS);
		}

		private boolean serviceTable() {
			return catalog == null || SERVICE_TABLE_ROLES.contains(catalog.role());
		}
	}

	private record FilterColumns(Set<String> email, Set<String> scope, Set<String> datetime) {
	}

	private record QueryShape(
			boolean selectsStar,
			boolean aggregate,
			boolean grouped,
			String whereClause,
			FilterColumns filterColumns) {

		private boolean hasEmailDateOrScopeFilter() {
			return hasEmailFilter() || hasDateFilter() || hasScopeFilter();
		}

		private boolean hasEmailFilter() {
			return referencesAny(filterColumns.email());
		}

		private boolean hasScopeFilter() {
			return referencesAny(filterColumns.scope());
		}

		private boolean hasDateFilter() {
			return referencesAny(filterColumns.datetime());
		}

		private boolean referencesAny(Set<String> columnNames) {
			for (var columnName : columnNames) {
				var pattern = Pattern.compile("(^|[^a-z0-9_])(?:[a-z0-9_]+\\.)?"
						+ Pattern.quote(columnName)
						+ "($|[^a-z0-9_])");
				if (pattern.matcher(whereClause).find()) {
					return true;
				}
			}
			return false;
		}
	}
}
