package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.DatabaseCatalog;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.config.AiQueryProperties;
import org.parkjw.agent.backoffice.security.AccessContext;
import org.parkjw.agent.backoffice.security.AccessRole;

import org.junit.jupiter.api.Test;

class SqlAccessGuardTest {

	private final SqlAnalyzer analyzer = new SqlAnalyzer();
	private final SqlAccessGuard guard = new SqlAccessGuard(properties(), analyzer);
	private final AccessContext scopedActor = new AccessContext(
			"operator-123",
			AccessRole.SCOPED,
			List.of("example.org"),
			List.of("service_catalog"),
			true);
	private final AccessContext globalAccess = new AccessContext(
			"root@example.org",
			AccessRole.GLOBAL,
			List.of(),
			List.of("service_catalog"),
			true);
	private final CatalogSnapshot catalog = new CatalogSnapshot(
			java.time.Instant.EPOCH,
			"test",
			"MySQL",
			"8",
			List.of(new DatabaseCatalog("service_catalog", "scope_candidate", "abc", List.of(
					table("users"),
					table("message_events"),
					table("admin_assignment"),
					table("domain_policy")))),
			List.of());

	@Test
	void inspect_whenSqlQueriesSystemSchema() {
		// given
		var sql = "select table_name from information_schema.tables";

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, scopedActor, catalog))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("blocked system");
	}

	@Test
	void inspect_whenSqlQueriesPasswordColumn() {
		// given
		var sql = "select email, password from users where domain = 'example.org'";

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, scopedActor, catalog))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("secret");
	}

	@Test
	void inspect_whenSqlUsesSystemFunction() {
		// given
		var sql = "select domain, version() from users where domain = 'example.org'";

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, scopedActor, catalog))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("blocked system function");
	}

	@Test
	void inspect_whenSqlQueriesSensitivePolicyTable() {
		// given
		var sql = "select domain, min_length from password_policy where domain = 'example.org'";

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, scopedActor, catalog))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("secret");
	}

	@Test
	void inspect_whenSqlReferencesOtherDomain() {
		// given
		var sql = "select email from users where domain = 'other.example.org'";

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, scopedActor, catalog))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("outside");
	}

	@Test
	void inspect_whenSqlIsInScopeServiceData() {
		// given
		var sql = "select email, status from users where domain = 'example.org'";

		// when / then
		assertThatCode(() -> guard.inspect(sql, scopedActor, catalog)).doesNotThrowAnyException();
	}

	@Test
	void inspect_whenGlobalActorQueriesServiceAdminAccountTable() {
		// given
		var sql = "select admin_id, email from service_catalog.admin_assignment";

		// when / then
		assertThatCode(() -> guard.inspect(sql, globalAccess, catalog)).doesNotThrowAnyException();
	}

	@Test
	void inspect_whenGlobalActorQueriesSystemPolicyTable() {
		// given
		var sql = "select domain, policy_name from service_catalog.domain_policy";

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, globalAccess, catalog))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("non-service-usage");
	}

	@Test
	void inspect_whenGlobalActorQueriesServiceUsageData() {
		// given
		var sql = "select email, created_at from message_events";

		// when / then
		assertThatCode(() -> guard.inspect(sql, globalAccess, catalog)).doesNotThrowAnyException();
	}

	private TableCatalog table(String name) {
		return new TableCatalog("service_catalog", name, "TABLE", null, -1L, List.of(), List.of(), List.of());
	}

	private AiQueryProperties properties() {
		return new AiQueryProperties(
				null,
				null,
				null,
				null,
				null,
				null);
	}
}
