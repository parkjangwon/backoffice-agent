package org.parkjw.agent.backoffice.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ScopeRelationshipInferer {

	private static final Set<String> SCOPE_TOKENS = Set.of(
			"tenant",
			"domain",
			"company",
			"organization",
			"org",
			"group",
			"customer",
			"client",
			"site",
			"realm",
			"workspace",
			"institution",
			"agency",
			"department");
	private static final Set<String> KEY_TOKENS = Set.of("uid", "id", "key", "code", "seq", "no");
	private static final Set<String> LABEL_TOKENS = Set.of("name", "title", "label", "display", "code", "host", "realm");

	List<ScopeRelationshipHint> infer(List<IndexedTable> tables) {
		var descriptors = scopeDescriptors(tables);
		var hints = new ArrayList<ScopeRelationshipHint>();
		for (var descriptor : descriptors) {
			for (var table : tables) {
				if (sameTable(table, descriptor.table())) {
					continue;
				}
				for (var column : table.table().columns()) {
					if (sameKey(column, descriptor)) {
						hints.add(hint(table, column, descriptor));
					}
				}
			}
		}
		return hints.stream()
				.filter(hint -> hint.confidence() >= 0.65)
				.sorted(java.util.Comparator.comparingDouble(ScopeRelationshipHint::confidence).reversed())
				.toList();
	}

	private List<ScopeDescriptor> scopeDescriptors(List<IndexedTable> tables) {
		var descriptors = new ArrayList<ScopeDescriptor>();
		for (var table : tables) {
			var conceptToken = conceptToken(table);
			if (conceptToken == null) {
				continue;
			}
			var keyColumn = table.table().columns().stream()
					.filter(column -> scopeKeyColumn(column, conceptToken))
					.findFirst();
			var labelColumn = table.table().columns().stream()
					.filter(column -> scopeLabelColumn(column, conceptToken))
					.findFirst();
			if (keyColumn.isPresent() && labelColumn.isPresent()) {
				descriptors.add(new ScopeDescriptor(table, keyColumn.get(), labelColumn.get(), conceptToken));
			}
		}
		return descriptors;
	}

	private ScopeRelationshipHint hint(IndexedTable table, ColumnCatalog column, ScopeDescriptor descriptor) {
		return new ScopeRelationshipHint(
				table.databaseName(),
				table.tableName(),
				column.name(),
				descriptor.table().tableName(),
				descriptor.keyColumn().name(),
				descriptor.labelColumn().name(),
				descriptor.conceptToken(),
				relationshipConfidence(table, column, descriptor),
				relationshipEvidence(table, column, descriptor));
	}

	private boolean sameTable(IndexedTable table, IndexedTable scopeTable) {
		return !table.normalizedDatabaseName().equals(scopeTable.normalizedDatabaseName())
				|| table.qualifiedName().equals(scopeTable.qualifiedName());
	}

	private String conceptToken(IndexedTable table) {
		for (var token : SCOPE_TOKENS) {
			if (table.normalizedTableName().contains(token) || table.tokens().contains(token)) {
				return token;
			}
		}
		return null;
	}

	private boolean scopeKeyColumn(ColumnCatalog column, String conceptToken) {
		var name = normalizeToken(column.name());
		return name.contains(conceptToken) && KEY_TOKENS.stream().anyMatch(name::contains);
	}

	private boolean scopeLabelColumn(ColumnCatalog column, String conceptToken) {
		var name = normalizeToken(column.name());
		var text = normalizeToken(column.name() + " " + column.remarks() + " " + column.displayName() + " " + column.label());
		return !emailPattern(name)
				&& (name.contains(conceptToken) || text.contains(conceptToken))
				&& LABEL_TOKENS.stream().anyMatch(text::contains);
	}

	private boolean sameKey(ColumnCatalog column, ScopeDescriptor descriptor) {
		return normalizeToken(column.name()).equals(normalizeToken(descriptor.keyColumn().name()))
				&& normalize(column.typeName()).equals(normalize(descriptor.keyColumn().typeName()));
	}

	private double relationshipConfidence(IndexedTable table, ColumnCatalog column, ScopeDescriptor descriptor) {
		var confidence = 0.45;
		confidence += table.tokens().contains(descriptor.conceptToken()) ? 0.1 : 0;
		confidence += table.table().indexed() ? 0.1 : 0;
		confidence += descriptor.table().table().primaryKeys().stream()
				.anyMatch(key -> normalizeToken(key).equals(normalizeToken(descriptor.keyColumn().name()))) ? 0.2 : 0;
		confidence += descriptor.table().table().indexed() ? 0.05 : 0;
		confidence += scopeLabelColumn(descriptor.labelColumn(), descriptor.conceptToken()) ? 0.1 : 0;
		confidence += scopeKeyColumn(column, descriptor.conceptToken()) ? 0.1 : 0;
		return Math.min(1, confidence);
	}

	private List<String> relationshipEvidence(IndexedTable table, ColumnCatalog column, ScopeDescriptor descriptor) {
		var evidence = new ArrayList<String>();
		evidence.add("same key column name and type");
		evidence.add("scope concept token: " + descriptor.conceptToken());
		if (table.table().indexed()) {
			evidence.add("source table has index metadata");
		}
		if (descriptor.table().table().indexed()) {
			evidence.add("scope table has index metadata");
		}
		if (descriptor.table().table().primaryKeys().stream()
				.anyMatch(key -> normalizeToken(key).equals(normalizeToken(descriptor.keyColumn().name())))) {
			evidence.add("scope key is primary key");
		}
		if (scopeKeyColumn(column, descriptor.conceptToken())) {
			evidence.add("source key name carries scope concept");
		}
		return evidence;
	}

	private boolean emailPattern(String value) {
		return value.matches(".*(emailid|emailaddress|emailaddr|mailaddress|mailaddr|email|mail)$");
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
	}

	private String normalizeToken(String value) {
		return normalize(value).replace("_", "");
	}

	private record ScopeDescriptor(
			IndexedTable table,
			ColumnCatalog keyColumn,
			ColumnCatalog labelColumn,
			String conceptToken) {
	}
}
