package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.DatabaseCatalog;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.catalog.ScopeMapping;
import org.parkjw.agent.backoffice.security.AccessContext;
import org.parkjw.agent.backoffice.security.AccessRole;

import org.junit.jupiter.api.Test;

class SqlCatalogGroundingValidatorTest {

	private final SqlCatalogGroundingValidator validator = new SqlCatalogGroundingValidator(new CatalogScopeResolver(), new SqlAnalyzer());
	private final AccessContext scopedActor = new AccessContext(
			"operator-123",
			AccessRole.SCOPED,
			List.of("example.org"),
			List.of(),
			true);
	private final CatalogSnapshot catalog = new CatalogSnapshot(
			Instant.parse("2026-06-19T00:00:00Z"),
			"test",
			"MySQL",
			"8",
			List.of(
					new DatabaseCatalog("master_catalog", "tenant", "abc", List.of(table("source_event"))),
					new DatabaseCatalog("tenant_example", "tenant", "abc", List.of(table("tenant_event")))),
			List.of(new ScopeMapping("example.org", "tenant_example", "master_catalog", "tenant_example")));

	@Test
	void requireCatalogTables_whenTableIsMappedAndFullyQualified() {
		// given
		var sql = "select * from `tenant_example`.`tenant_event`";

		// when / then
		assertThatCode(() -> validator.requireCatalogTables(sql, catalog, scopedActor)).doesNotThrowAnyException();
	}

	@Test
	void requireCatalogTables_whenTableIsUnqualified() {
		// given
		var sql = "select * from tenant_event";

		// when / then
		assertThatThrownBy(() -> validator.requireCatalogTables(sql, catalog, scopedActor))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("fully-qualified");
	}

	@Test
	void requireCatalogTables_whenTableBelongsToSourceDatabase() {
		// given
		var sql = "select * from `master_catalog`.`source_event`";

		// when / then
		assertThatThrownBy(() -> validator.requireCatalogTables(sql, catalog, scopedActor))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("outside");
	}

	@Test
	void requireCatalogTables_whenSqlUsesCte_checksUnderlyingTables() {
		// given
		var sql = """
				with events as (
				    select * from `tenant_example`.`tenant_event`
				)
				select * from events
				""";

		// when / then
		assertThatThrownBy(() -> validator.requireCatalogTables(sql, catalog, scopedActor))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("WITH clauses (CTE) are blocked");
	}

	private TableCatalog table(String name) {
		return new TableCatalog("tenant_example", name, "TABLE", null, 10L, List.of(), List.of(), List.of());
	}
}
