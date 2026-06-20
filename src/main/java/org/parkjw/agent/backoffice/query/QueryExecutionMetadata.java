package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.Map;

import org.parkjw.agent.backoffice.security.AccessRole;

public record QueryExecutionMetadata(
		String requestId,
		String action,
		String outcome,
		long latencyMs,
		boolean cacheHit,
		String catalogVersion,
		AccessRole effectiveRole,
		List<String> effectiveScopeValues,
		List<String> effectiveDatabases,
		Integer limit,
		int rowCount,
		String sqlHash,
		Map<String, Long> stageLatencyMs) {

	public QueryExecutionMetadata(
			String requestId,
			String action,
			String outcome,
			long latencyMs,
			boolean cacheHit,
			String catalogVersion,
			AccessRole effectiveRole,
			List<String> effectiveScopeValues,
			List<String> effectiveDatabases,
			Integer limit,
			int rowCount,
			String sqlHash) {
		this(
				requestId,
				action,
				outcome,
				latencyMs,
				cacheHit,
				catalogVersion,
				effectiveRole,
				effectiveScopeValues,
				effectiveDatabases,
				limit,
				rowCount,
				sqlHash,
				Map.of());
	}

	public QueryExecutionMetadata {
		effectiveScopeValues = effectiveScopeValues == null ? List.of() : List.copyOf(effectiveScopeValues);
		effectiveDatabases = effectiveDatabases == null ? List.of() : List.copyOf(effectiveDatabases);
		stageLatencyMs = stageLatencyMs == null ? Map.of() : Map.copyOf(stageLatencyMs);
	}
}
