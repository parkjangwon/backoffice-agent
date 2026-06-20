package org.parkjw.agent.backoffice.query;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.parkjw.agent.backoffice.catalog.CatalogSearchIndex;
import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.ScopeRelationshipHint;
import org.parkjw.agent.backoffice.catalog.ScopeMapping;
import org.parkjw.agent.backoffice.security.AccessContext;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;

import org.springframework.stereotype.Component;

@Component
public class ScopeEnforcer {

	private final SqlAnalyzer analyzer;

	public ScopeEnforcer(SqlAnalyzer analyzer) {
		this.analyzer = analyzer;
	}

	public String enforce(String sql, AccessContext accessContext) {
		return enforce(sql, accessContext, CatalogSnapshot.empty(), CatalogSearchIndex.empty());
	}

	public String enforce(String sql, AccessContext accessContext, CatalogSnapshot catalog, CatalogSearchIndex index) {
		catalog = catalog == null ? CatalogSnapshot.empty() : catalog;
		index = index == null ? CatalogSearchIndex.empty() : index;
		if (accessContext.globalAccess()) {
			return sql;
		}
		if (accessContext.scopeValues().isEmpty()) {
			throw new SqlPolicyException("Scoped actor has no allowed scope.");
		}
		var parsed = analyzer.requireSingleSelect(sql);
		if (mappedScopeDatabaseProvesScope(parsed, catalog, accessContext)) {
			return sql;
		}
		return injectScopePredicate(parsed, accessContext, catalog, index)
				.orElseThrow(() -> new SqlPolicyException("Scoped SQL could not prove scope from catalog relationships."));
	}

	private Set<String> mappedDatabases(CatalogSnapshot catalog, AccessContext accessContext) {
		return catalog.scopeMappings().stream()
				.filter(mapping -> scopeAllowed(mapping, accessContext))
				.map(ScopeMapping::mappedDatabase)
				.filter(value -> value != null && !value.isBlank())
				.map(value -> value.toLowerCase(Locale.ROOT))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private boolean mappedScopeDatabaseProvesScope(
			SqlAnalyzer.ParsedSql parsed,
			CatalogSnapshot catalog,
			AccessContext accessContext) {
		var mappedDatabases = mappedDatabases(catalog, accessContext);
		if (mappedDatabases.isEmpty() || parsed.tableReferences().isEmpty()) {
			return false;
		}
		return parsed.tableReferences().stream()
				.allMatch(reference -> {
					var dot = reference.indexOf('.');
					return dot > 0 && mappedDatabases.contains(reference.substring(0, dot).toLowerCase(Locale.ROOT));
				});
	}

	private boolean scopeAllowed(ScopeMapping mapping, AccessContext accessContext) {
		return accessContext.scopeValues().stream()
				.anyMatch(scopeValue -> scopeValue.equalsIgnoreCase(mapping.scopeValue()));
	}

	private Optional<String> injectScopePredicate(
			SqlAnalyzer.ParsedSql parsed,
			AccessContext accessContext,
			CatalogSnapshot catalog,
			CatalogSearchIndex index) {
		var plainSelect = parsed.select().getPlainSelect();
		if (plainSelect == null) {
			return Optional.empty();
		}
		var tables = tables(plainSelect);
		if (tables.isEmpty()) {
			return Optional.empty();
		}
		var mappedDatabases = mappedDatabases(catalog, accessContext);
		var hints = index.scopeRelationshipHints(Set.of(), List.of());
		var injections = new ArrayList<ScopeInjection>();
		for (var table : tables) {
			var reference = reference(table);
			if (isMappedTable(reference, mappedDatabases)) {
				continue;
			}
			var dataHint = hints.stream()
					.filter(hint -> matchesDataTable(table, hint))
					.findFirst();
			if (dataHint.isPresent()) {
				injections.add(new ScopeInjection(table, dataHint.get(), true));
				continue;
			}
			var scopeHint = hints.stream()
					.filter(hint -> matchesScopeTable(table, hint))
					.findFirst();
			if (scopeHint.isPresent()) {
				injections.add(new ScopeInjection(table, scopeHint.get(), false));
				continue;
			}
			return Optional.empty();
		}
		if (injections.isEmpty()) {
			return Optional.empty();
		}
		injections.forEach(injection -> inject(plainSelect, injection.table(), injection.hint(), accessContext, injection.joinScopeTable()));
		return Optional.of(plainSelect.toString());
	}

	private List<Table> tables(PlainSelect plainSelect) {
		if (!(plainSelect.getFromItem() instanceof Table fromTable)) {
			return List.of();
		}
		var tables = new ArrayList<Table>();
		tables.add(fromTable);
		if (plainSelect.getJoins() == null) {
			return List.copyOf(tables);
		}
		for (var join : plainSelect.getJoins()) {
			if (!(join.getRightItem() instanceof Table table)) {
				return List.of();
			}
			tables.add(table);
		}
		return List.copyOf(tables);
	}

	private void inject(
			PlainSelect plainSelect,
			Table scopedTable,
			ScopeRelationshipHint hint,
			AccessContext accessContext,
			boolean joinScopeTable) {
		if (!joinScopeTable) {
			plainSelect.setWhere(and(plainSelect.getWhere(), allowedScopePredicate(tableQualifier(scopedTable), hint, accessContext)));
			return;
		}
		var scopeAlias = uniqueScopeAlias(plainSelect);
		var scopeTable = new Table(quoted(hint.databaseName()), quoted(hint.scopeTableName()));
		scopeTable.setAlias(new Alias(scopeAlias, false));

		var join = new Join();
		join.setInner(true);
		join.setRightItem(scopeTable);
		join.setOnExpression(new EqualsTo(
				column(tableQualifier(scopedTable), hint.keyColumnName()),
				column(scopeAlias, hint.scopeKeyColumnName())));
		plainSelect.addJoins(join);
		plainSelect.setWhere(and(plainSelect.getWhere(), allowedScopePredicate(scopeAlias, hint, accessContext)));
	}

	private Expression allowedScopePredicate(String scopeAlias, ScopeRelationshipHint hint, AccessContext accessContext) {
		var values = accessContext.scopeValues().stream()
				.map(StringValue::new)
				.toList();
		return new InExpression(
				column(scopeAlias, hint.scopeLabelColumnName()),
				new ParenthesedExpressionList<>(values));
	}

	private Expression and(Expression left, Expression right) {
		return left == null ? right : new AndExpression(left, right);
	}

	private Column column(String tableQualifier, String columnName) {
		return new Column(new Table(tableQualifier), quoted(columnName));
	}

	private String tableQualifier(Table table) {
		var alias = table.getAlias();
		if (alias != null && alias.getUnquotedName() != null && !alias.getUnquotedName().isBlank()) {
			return alias.getUnquotedName();
		}
		return table.getUnquotedName();
	}

	private boolean matchesDataTable(Table table, ScopeRelationshipHint hint) {
		return normalize(table.getUnquotedSchemaName()).equals(normalize(hint.databaseName()))
				&& normalize(table.getUnquotedName()).equals(normalize(hint.tableName()));
	}

	private boolean matchesScopeTable(Table table, ScopeRelationshipHint hint) {
		return normalize(table.getUnquotedSchemaName()).equals(normalize(hint.databaseName()))
				&& normalize(table.getUnquotedName()).equals(normalize(hint.scopeTableName()));
	}

	private boolean isMappedTable(String reference, Set<String> mappedDatabases) {
		var dot = reference.indexOf('.');
		return dot > 0 && mappedDatabases.contains(reference.substring(0, dot).toLowerCase(Locale.ROOT));
	}

	private String reference(Table table) {
		return reference(table.getUnquotedSchemaName(), table.getUnquotedName());
	}

	private String uniqueScopeAlias(PlainSelect select) {
		var aliases = new ArrayList<String>();
		if (select.getFromItem() instanceof Table table && table.getAlias() != null) {
			aliases.add(table.getAlias().getUnquotedName());
		}
		if (select.getJoins() != null) {
			for (var join : select.getJoins()) {
				if (join.getRightItem() instanceof Table table && table.getAlias() != null) {
					aliases.add(table.getAlias().getUnquotedName());
				}
			}
		}
		for (var index = 0; index < 100; index++) {
			var alias = "aiq_scope_" + index;
			if (aliases.stream().noneMatch(value -> alias.equalsIgnoreCase(value))) {
				return alias;
			}
		}
		throw new SqlPolicyException("Could not allocate a scope alias.");
	}

	private String reference(String databaseName, String tableName) {
		return normalize(databaseName) + "." + normalize(tableName);
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("`", "").replace("\"", "");
	}

	private String quoted(String value) {
		return "`" + value.replace("`", "``") + "`";
	}

	private record ScopeInjection(Table table, ScopeRelationshipHint hint, boolean joinScopeTable) {
	}
}
