package org.parkjw.agent.backoffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.parkjw.agent.backoffice.query.QueryRequest;
import org.parkjw.agent.backoffice.query.ResponseFormat;
import org.parkjw.agent.backoffice.query.SqlPolicyException;

import org.junit.jupiter.api.Test;

class AccessScopeResolverTest {

	private final AccessScopeResolver resolver = new AccessScopeResolver();

	@Test
	void resolve_whenRoleIsGlobal_usesScannedCatalogScope() {
		var request = request(
				"root",
				AccessRole.GLOBAL,
				List.of());

		var scope = resolver.resolve(request);

		assertThat(scope).isEqualTo(new EffectiveAccessScope(
				"root",
				AccessRole.GLOBAL,
				List.of(),
				List.of(),
				true));
		assertThat(scope.globalAccess()).isTrue();
	}

	@Test
	void resolve_whenRoleIsScoped_usesExplicitScopeValues() {
		var request = request(
				"operator-123",
				AccessRole.SCOPED,
				List.of("Acme", "acme", "North"));

		var scope = resolver.resolve(request);

		assertThat(scope).isEqualTo(new EffectiveAccessScope(
				"operator-123",
				AccessRole.SCOPED,
				List.of("acme", "north"),
				List.of(),
				true));
		assertThat(scope.globalAccess()).isFalse();
	}

	@Test
	void resolve_whenActorAndScopeHaveUppercase_normalizesBoth() {
		var request = request(
				"Operator-ABC",
				AccessRole.SCOPED,
				List.of("Tenant-A"));

		var scope = resolver.resolve(request);

		assertThat(scope.actorId()).isEqualTo("operator-abc");
		assertThat(scope.scopeValues()).containsExactly("tenant-a");
	}

	@Test
	void resolve_whenScopedRoleHasNoScopeValues_failsClosed() {
		var request = request(
				"operator-123",
				AccessRole.SCOPED,
				List.of());

		assertThatThrownBy(() -> resolver.resolve(request))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("at least one scope value");
	}

	private QueryRequest request(
			String actorId,
			AccessRole role,
			List<String> scopeValues) {
		return new QueryRequest(
				actorId,
				role,
				scopeValues,
				"show usage",
				ResponseFormat.JSON,
				100);
	}
}
