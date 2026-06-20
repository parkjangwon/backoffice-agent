package org.parkjw.agent.backoffice.security;

import java.util.List;

public record AccessContext(
		String actorId,
		AccessRole role,
		List<String> scopeValues,
		List<String> allowedDatabases,
		boolean exportAllowed) {

	public AccessContext {
		scopeValues = scopeValues == null ? List.of() : List.copyOf(scopeValues);
		allowedDatabases = allowedDatabases == null ? List.of() : List.copyOf(allowedDatabases);
	}

	public boolean globalAccess() {
		return role == AccessRole.GLOBAL;
	}
}
