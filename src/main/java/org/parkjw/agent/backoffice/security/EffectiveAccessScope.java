package org.parkjw.agent.backoffice.security;

import java.util.List;

public record EffectiveAccessScope(
		String actorId,
		AccessRole role,
		List<String> scopeValues,
		List<String> allowedDatabases,
		boolean exportAllowed) {

	public EffectiveAccessScope {
		scopeValues = scopeValues == null ? List.of() : List.copyOf(scopeValues);
		allowedDatabases = allowedDatabases == null ? List.of() : List.copyOf(allowedDatabases);
	}

	public boolean globalAccess() {
		return role == AccessRole.GLOBAL;
	}

	public AccessContext toAccessContext() {
		return new AccessContext(actorId, role, scopeValues, allowedDatabases, exportAllowed);
	}
}
