package org.parkjw.agent.backoffice.catalog;

import java.util.List;
import java.util.Locale;

public final class TableRoleResolver {

	private TableRoleResolver() {
	}

	public static TableRole role(Input input) {
		return scores(input).role();
	}

	public static double confidence(Input input) {
		var scores = scores(input);
		if (scores.role() == TableRole.UNKNOWN) {
			return 0.2;
		}
		return Math.min(0.95, 0.45 + (scores.topScore() * 0.04) + (scores.margin() * 0.03));
	}

	private static RoleScores scores(Input input) {
		var text = normalized(input.tableName() + " " + input.remarks());
		var tenantColumns = input.columns().stream().filter(ColumnCatalog::scopeCandidate).count();
		var emailColumns = semanticCount(input, ColumnSemanticType.EMAIL);
		var dateColumns = semanticCount(input, ColumnSemanticType.DATETIME);
		var statusColumns = semanticCount(input, ColumnSemanticType.STATUS);
		var sizeColumns = semanticCount(input, ColumnSemanticType.BYTE_SIZE);
		var countColumns = semanticCount(input, ColumnSemanticType.COUNT);
		var indexedColumns = input.usableIndexColumns().size();

		var tenantScore = score(text, "tenant", "domain", "organization", "org", "customer", "workspace")
				+ (tenantColumns * 4)
				+ (indexedColumns > 0 && tenantColumns > 0 ? 2 : 0);
		var accountScore = score(text, "account", "user", "member", "profile", "principal")
				+ (emailColumns * 4)
				+ (tenantColumns > 0 ? 1 : 0);
		var eventScore = score(text, "event", "log", "history", "audit", "activity", "trace")
				+ (dateColumns * 3)
				+ (statusColumns * 2)
				+ (emailColumns > 0 ? 1 : 0)
				+ (input.estimatedRows() > 0 ? 1 : 0);
		var metricScore = score(text, "metric", "stat", "summary", "aggregate", "usage", "quota")
				+ (sizeColumns * 3)
				+ (countColumns * 3)
				+ (dateColumns > 0 ? 1 : 0);
		var lookupScore = score(text, "lookup", "code", "type", "category", "status", "state", "reference")
				+ (statusColumns * 3)
				+ (input.estimatedRows() >= 0 && input.estimatedRows() <= 500 ? 1 : 0);
		var contentScore = score(text, "content", "message", "document", "file", "body", "text", "attachment")
				+ (sizeColumns * 2);
		var relationshipScore = score(text, "map", "mapping", "join", "link", "relation", "assignment")
				+ Math.min(3, input.primaryKeys().size() + indexedColumns);

		return bestScore(new int[] {
				(int) tenantScore,
				(int) accountScore,
				(int) eventScore,
				(int) metricScore,
				(int) lookupScore,
				(int) contentScore,
				(int) relationshipScore});
	}

	private static RoleScores bestScore(int[] values) {
		var roles = new TableRole[] {
				TableRole.TENANT,
				TableRole.ACCOUNT,
				TableRole.EVENT,
				TableRole.METRIC,
				TableRole.LOOKUP,
				TableRole.CONTENT,
				TableRole.RELATIONSHIP};
		var topIndex = -1;
		var topScore = 0;
		var secondScore = 0;
		for (var index = 0; index < values.length; index++) {
			if (values[index] > topScore) {
				secondScore = topScore;
				topScore = values[index];
				topIndex = index;
			}
			else if (values[index] > secondScore) {
				secondScore = values[index];
			}
		}
		if (topIndex < 0 || topScore < 3) {
			return new RoleScores(TableRole.UNKNOWN, topScore, topScore - secondScore);
		}
		return new RoleScores(roles[topIndex], topScore, topScore - secondScore);
	}

	private static long semanticCount(Input input, ColumnSemanticType semanticType) {
		return input.columns().stream()
				.filter(column -> column.semanticType() == semanticType)
				.count();
	}

	private static int score(String value, String... tokens) {
		var score = 0;
		for (var token : tokens) {
			if (value.contains(token)) {
				score += 3;
			}
		}
		return score;
	}

	private static String normalized(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "");
	}

	public record Input(
			String tableName,
			String remarks,
			long estimatedRows,
			List<ColumnCatalog> columns,
			List<String> primaryKeys,
			List<String> indexes,
			List<String> usableIndexColumns) {

		public Input {
			columns = columns == null ? List.of() : List.copyOf(columns);
			primaryKeys = primaryKeys == null ? List.of() : List.copyOf(primaryKeys);
			indexes = indexes == null ? List.of() : List.copyOf(indexes);
			usableIndexColumns = usableIndexColumns == null ? List.of() : List.copyOf(usableIndexColumns);
		}
	}

	private record RoleScores(TableRole role, int topScore, int margin) {
	}
}
