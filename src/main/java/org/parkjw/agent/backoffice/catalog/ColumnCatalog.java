package org.parkjw.agent.backoffice.catalog;

public record ColumnCatalog(
		String name,
		String typeName,
		int size,
		boolean nullable,
		String remarks,
		boolean scopeCandidate,
		ColumnSemanticType semanticType,
		ColumnUnit unit,
		String displayName,
		String label,
		Double semanticConfidence) {

	public ColumnCatalog(
			String name,
			String typeName,
			int size,
			boolean nullable,
			String remarks,
			boolean scopeCandidate) {
		this(
				name,
				typeName,
				size,
				nullable,
				remarks,
				scopeCandidate,
				null,
				null,
				null,
				null,
				null);
	}

	public ColumnCatalog(
			String name,
			String typeName,
			int size,
			boolean nullable,
			String remarks,
			boolean scopeCandidate,
			ColumnSemanticType semanticType,
			ColumnUnit unit,
			String displayName) {
		this(
				name,
				typeName,
				size,
				nullable,
				remarks,
				scopeCandidate,
				semanticType,
				unit,
				displayName,
				null,
				null);
	}

	public ColumnCatalog {
		semanticType = semanticType == null ? ColumnSemanticResolver.semanticType(name, typeName, remarks) : semanticType;
		unit = unit == null ? ColumnSemanticResolver.unit(name, typeName, remarks, semanticType) : unit;
		displayName = displayName == null || displayName.isBlank() ? ColumnSemanticResolver.displayName(name, remarks) : displayName;
		label = label == null || label.isBlank() ? ColumnSemanticResolver.label(name, remarks) : label;
		semanticConfidence = semanticConfidence == null || semanticConfidence <= 0
				? ColumnSemanticResolver.semanticConfidence(name, typeName, remarks, semanticType)
				: Math.min(1, semanticConfidence);
	}
}
