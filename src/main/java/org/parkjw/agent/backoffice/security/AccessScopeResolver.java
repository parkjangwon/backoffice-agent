package org.parkjw.agent.backoffice.security;

import java.util.List;
import java.util.Locale;

import org.parkjw.agent.backoffice.query.QueryRequest;
import org.parkjw.agent.backoffice.query.SqlPolicyException;

import org.springframework.stereotype.Component;

@Component
public class AccessScopeResolver {

	public EffectiveAccessScope resolve(QueryRequest request) {
		var actorId = normalize(request.actorId());
		var role = request.role();
		if (role == null) {
			throw new SqlPolicyException("Access role is required.");
		}
		if (role == AccessRole.GLOBAL) {
			return new EffectiveAccessScope(actorId, role, List.of(), List.of(), true);
		}
		if (request.scopeValues().isEmpty()) {
			throw new SqlPolicyException("SCOPED access requires at least one scope value.");
		}
		return new EffectiveAccessScope(actorId, role, normalizedScopes(request.scopeValues()), List.of(), true);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static List<String> normalizedScopes(List<String> values) {
		return values.stream()
				.map(AccessScopeResolver::normalize)
				.filter(value -> !value.isBlank())
				.distinct()
				.toList();
	}
}
