package org.parkjw.agent.backoffice.catalog;

import java.util.List;

public record ScopeRelationshipHint(
		String databaseName,
		String tableName,
		String keyColumnName,
		String scopeTableName,
		String scopeKeyColumnName,
		String scopeLabelColumnName,
		String conceptToken,
		double confidence,
		List<String> evidence) {

	public ScopeRelationshipHint {
		evidence = evidence == null ? List.of() : List.copyOf(evidence);
		confidence = Math.max(0, Math.min(1, confidence));
	}

	public String summary() {
		return "`%s`.`%s`.`%s` -> `%s`.`%s`.`%s` label `%s`".formatted(
				databaseName,
				tableName,
				keyColumnName,
				databaseName,
				scopeTableName,
				scopeKeyColumnName,
				scopeLabelColumnName);
	}
}
