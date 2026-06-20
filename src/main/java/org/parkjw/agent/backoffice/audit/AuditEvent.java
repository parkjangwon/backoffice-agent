package org.parkjw.agent.backoffice.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.parkjw.agent.backoffice.security.AccessRole;

public record AuditEvent(
		Instant occurredAt,
		String actorId,
		AccessRole role,
		String promptHash,
		String sqlHash,
		int rowCount,
		String action,
		String requestId,
		String outcome,
		long latencyMs,
		boolean cacheHit,
		String catalogVersion,
		AccessRole effectiveRole,
		List<String> effectiveScopeValues,
		List<String> effectiveDatabases,
		Integer limit,
		Map<String, Long> stageLatencyMs) {

	public AuditEvent(
			Instant occurredAt,
			String actorId,
			AccessRole role,
			String promptHash,
			String sqlHash,
			int rowCount,
			String action,
			String requestId,
			String outcome,
			long latencyMs,
			boolean cacheHit,
			String catalogVersion,
			AccessRole effectiveRole,
			List<String> effectiveScopeValues,
			List<String> effectiveDatabases,
			Integer limit) {
		this(
				occurredAt,
				actorId,
				role,
				promptHash,
				sqlHash,
				rowCount,
				action,
				requestId,
				outcome,
				latencyMs,
				cacheHit,
				catalogVersion,
				effectiveRole,
				effectiveScopeValues,
				effectiveDatabases,
				limit,
				Map.of());
	}

	public AuditEvent {
		effectiveScopeValues = effectiveScopeValues == null ? List.of() : List.copyOf(effectiveScopeValues);
		effectiveDatabases = effectiveDatabases == null ? List.of() : List.copyOf(effectiveDatabases);
		stageLatencyMs = stageLatencyMs == null ? Map.of() : Map.copyOf(stageLatencyMs);
	}
}
