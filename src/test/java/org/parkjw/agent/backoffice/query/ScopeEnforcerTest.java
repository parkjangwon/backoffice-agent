package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.parkjw.agent.backoffice.catalog.CatalogSearchIndex;
import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.DatabaseCatalog;
import org.parkjw.agent.backoffice.catalog.ScopeRelationshipHint;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.catalog.ScopeMapping;
import org.parkjw.agent.backoffice.security.AccessContext;
import org.parkjw.agent.backoffice.security.AccessRole;

import org.junit.jupiter.api.Test;

class ScopeEnforcerTest {

	private final ScopeEnforcer enforcer = new ScopeEnforcer(new SqlAnalyzer());

	@Test
	void enforce_whenAdminIsGlobalActor() {
		// given
		var admin = new AccessContext("root", AccessRole.GLOBAL, List.of(), List.of(), true);
		var sql = "select * from users";

		// when
		var result = enforcer.enforce(sql, admin, CatalogSnapshot.empty(), CatalogSearchIndex.empty());

		// then
		assertThat(result).isEqualTo(sql);
	}

	@Test
	void enforce_whenScopedActorScopeCannotBeProven() {
		// given
		var admin = new AccessContext(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				List.of(),
				true);

		// when / then
		assertThatThrownBy(() -> enforcer.enforce("select id, email, domain from users", admin, CatalogSnapshot.empty(), CatalogSearchIndex.empty()))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("could not prove scope");
	}

	@Test
	void enforce_whenLiteralDomainAliasIsUsedOnUnprovenSharedTable_rejectsInsteadOfTrustingAlias() {
		// given
		var admin = new AccessContext(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				List.of(),
				true);
		var sql = "select email, 'example.org' as domain from `service_catalog`.`mailbox_usage`";

		// when / then
		assertThatThrownBy(() -> enforcer.enforce(sql, admin, singleSchemaCatalog(), CatalogSearchIndex.empty()))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("could not prove scope");
	}

	@Test
	void enforce_whenScopedActorSqlUsesMappedTenantDatabase_doesNotRequireDomainProjection() {
		// given
		var admin = new AccessContext(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				List.of(),
				true);
		var sql = "select email, used_size from `tenant_example`.`mailbox_usage` order by used_size desc";

		// when
		var result = enforcer.enforce(sql, admin, mappedTenantCatalog(), CatalogSearchIndex.empty());

		// then
		assertThat(result).isEqualTo(sql);
	}

	@Test
	void enforce_whenScopeRelationshipHintCanJoinScopeTable_injectsPredicateWithoutDomainProjection() {
		// given
		var admin = new AccessContext(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				List.of(),
				true);
		var sql = "select u.email, u.used_size from `service_catalog`.`mailbox_usage` u";
		var hint = new ScopeRelationshipHint(
				"service_catalog",
				"mailbox_usage",
				"company_uid",
				"company",
				"company_uid",
				"company_domain",
				"company",
				0.91,
				List.of("test"));
		var index = new CatalogSearchIndex(
				singleSchemaCatalog(),
				List.of(),
				List.of(hint),
				java.util.Map.of(),
				java.util.Map.of(),
				java.util.Map.of());

		// when
		var result = enforcer.enforce(sql, admin, singleSchemaCatalog(), index);

		// then
		assertThat(result.toLowerCase(java.util.Locale.ROOT))
				.contains("inner join `service_catalog`.`company` aiq_scope_")
				.contains("aiq_scope_")
				.contains("`company_domain` in ('example.org')");
	}

	@Test
	void enforce_whenJoinedTableCannotBeScoped_rejectsWholeQuery() {
		// given
		var admin = new AccessContext(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				List.of(),
				true);
		var sql = """
				select u.email, s.secret_value
				from `service_catalog`.`mailbox_usage` u
				join `service_catalog`.`system_secret` s on s.id = u.id
				""";
		var hint = new ScopeRelationshipHint(
				"service_catalog",
				"mailbox_usage",
				"company_uid",
				"company",
				"company_uid",
				"company_domain",
				"company",
				0.91,
				List.of("test"));
		var index = new CatalogSearchIndex(
				singleSchemaCatalog(),
				List.of(),
				List.of(hint),
				java.util.Map.of(),
				java.util.Map.of(),
				java.util.Map.of());

		// when / then
		assertThatThrownBy(() -> enforcer.enforce(sql, admin, singleSchemaCatalog(), index))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("could not prove scope");
	}

	@Test
	void enforce_whenEveryJoinedDataTableHasScopeHint_injectsPredicateForEachTable() {
		// given
		var admin = new AccessContext(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				List.of(),
				true);
		var sql = """
				select u.email, l.subject
				from `service_catalog`.`mailbox_usage` u
				join `service_catalog`.`mail_log` l on l.user_email = u.email
				""";
		var usageHint = new ScopeRelationshipHint(
				"service_catalog",
				"mailbox_usage",
				"company_uid",
				"company",
				"company_uid",
				"company_domain",
				"company",
				0.91,
				List.of("test"));
		var logHint = new ScopeRelationshipHint(
				"service_catalog",
				"mail_log",
				"company_uid",
				"company",
				"company_uid",
				"company_domain",
				"company",
				0.91,
				List.of("test"));
		var index = new CatalogSearchIndex(
				singleSchemaCatalog(),
				List.of(),
				List.of(usageHint, logHint),
				java.util.Map.of(),
				java.util.Map.of(),
				java.util.Map.of());

		// when
		var result = enforcer.enforce(sql, admin, singleSchemaCatalog(), index)
				.toLowerCase(java.util.Locale.ROOT);

		// then
		assertThat(result)
				.contains("u.`company_uid` = aiq_scope_")
				.contains("l.`company_uid` = aiq_scope_")
				.contains("`company_domain` in ('example.org')");
	}

	@Test
	void enforce_whenScopedActorHasNoScope() {
		// given
		var admin = new AccessContext("operator-123", AccessRole.SCOPED, List.of(), List.of(), true);

		// when / then
			assertThatThrownBy(() -> enforcer.enforce("select * from users", admin, CatalogSnapshot.empty(), CatalogSearchIndex.empty()))
					.isInstanceOf(SqlPolicyException.class)
					.hasMessageContaining("no allowed scope");
	}

	@Test
	void enforce_whenScopedActorSqlDoesNotProjectDomain() {
		// given
		var admin = new AccessContext(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				List.of(),
				true);

		// when / then
		assertThatThrownBy(() -> enforcer.enforce("select id, email from users", admin, CatalogSnapshot.empty(), CatalogSearchIndex.empty()))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("could not prove scope");
	}

	private CatalogSnapshot mappedTenantCatalog() {
		return new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"test",
				"MySQL",
				"8",
				List.of(new DatabaseCatalog("tenant_example", "tenant", "abc", List.of(table("tenant_example", "mailbox_usage")))),
				List.of(new ScopeMapping("example.org", "tenant_example", "master_catalog", "tenant_example")));
	}

	private CatalogSnapshot singleSchemaCatalog() {
		return new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"test",
				"MySQL",
				"8",
				List.of(new DatabaseCatalog("service_catalog", "service", "abc", List.of(
						table("service_catalog", "mailbox_usage"),
						table("service_catalog", "company"),
						table("service_catalog", "mail_log"),
						table("service_catalog", "system_secret")))),
				List.of());
	}

	private TableCatalog table(String databaseName, String name) {
		return new TableCatalog(databaseName, name, "TABLE", null, 10L, List.of(), List.of(), List.of());
	}
}
