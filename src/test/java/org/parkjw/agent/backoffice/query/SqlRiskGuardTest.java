package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.ColumnCatalog;
import org.parkjw.agent.backoffice.catalog.ColumnSemanticType;
import org.parkjw.agent.backoffice.catalog.DatabaseCatalog;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.catalog.TableRole;
import org.parkjw.agent.backoffice.security.AccessContext;
import org.parkjw.agent.backoffice.security.AccessRole;

import org.junit.jupiter.api.Test;

class SqlRiskGuardTest {

	private final SqlRiskGuard guard = new SqlRiskGuard(new SqlAnalyzer());
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
			Instant.parse("2026-06-19T00:00:00Z"),
			"test",
			"MySQL",
			"8",
			List.of(new DatabaseCatalog("service_catalog", "scope_candidate", "abc", List.of(
					table("mail_events", 2_000_000L, TableRole.EVENT),
					table("mailbox_usage", 800_000L, TableRole.METRIC),
					table("user_accounts", -1L, TableRole.ACCOUNT),
					table("daily_summary", 50L, TableRole.METRIC)))),
			List.of());

	@Test
	void inspect_whenScopedActorExportsLargeTableWithoutBusinessFilter_rejectsBroadQuery() {
		// given
		var sql = "select email, subject from service_catalog.mail_events";
		var intent = intent(BusinessIntentCategory.MAIL_SEARCH, Set.of(), Set.of(), "Resolved date range: none.");

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, scopedActor, intent, catalog, "EXPORT"))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("broad export");
	}

	@Test
	void inspect_whenExactEmailServiceQueryOnLargeTable_allowsQuery() {
		// given
		var sql = "select email, subject from service_catalog.mail_events where email = 'user@example.org'";
		var intent = intent(BusinessIntentCategory.MAIL_SEARCH, Set.of("user@example.org"), Set.of("example.org"), "Resolved date range: none.");

		// when / then
		assertThatCode(() -> guard.inspect(sql, scopedActor, intent, catalog, "PREVIEW")).doesNotThrowAnyException();
	}

	@Test
	void inspect_whenLargeServiceTableUsesSelectStar_rejectsWideProjection() {
		// given
		var sql = "select * from service_catalog.mail_events where domain = 'example.org'";
		var intent = intent(BusinessIntentCategory.SERVICE_USAGE, Set.of(), Set.of("example.org"), "Resolved date range: none.");

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, scopedActor, intent, catalog, "PREVIEW"))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("select *");
	}

	@Test
	void inspect_whenSmallCountAggregateHasNoBusinessFilter_allowsQuery() {
		// given
		var sql = "select count(*) from service_catalog.daily_summary";
		var intent = intent(BusinessIntentCategory.STATISTICS, Set.of(), Set.of(), "Resolved date range: none.");

		// when / then
		assertThatCode(() -> guard.inspect(sql, globalAccess, intent, catalog, "PREVIEW")).doesNotThrowAnyException();
	}

	@Test
	void inspect_whenMailboxQueryHasDomainFilter_allowsQuery() {
		// given
		var sql = "select domain, used_bytes from service_catalog.mailbox_usage where domain = 'example.org'";
		var intent = intent(BusinessIntentCategory.MAILBOX_USAGE, Set.of(), Set.of("example.org"), "Resolved date range: none.");

		// when / then
		assertThatCode(() -> guard.inspect(sql, scopedActor, intent, catalog, "PREVIEW")).doesNotThrowAnyException();
	}

	@Test
	void inspect_whenLoginQueryHasDateFilter_allowsQuery() {
		// given
		var sql = "select email, created_at from service_catalog.mail_events where created_at >= '2026-06-01'";
		var intent = intent(
				BusinessIntentCategory.LOGIN_HISTORY,
				Set.of(),
				Set.of(),
				"Resolved date range: created_at >= '2026-06-01 00:00:00'.");

		// when / then
		assertThatCode(() -> guard.inspect(sql, scopedActor, intent, catalog, "PREVIEW")).doesNotThrowAnyException();
	}

	@Test
	void inspect_whenUserStatusQueryHasStatusFilter_allowsQuery() {
		// given
		var sql = "select email, status from service_catalog.user_accounts where status = 'LOCKED'";
		var intent = intent(BusinessIntentCategory.USER_STATUS, Set.of(), Set.of(), "Resolved date range: none.");

		// when / then
		assertThatCode(() -> guard.inspect(sql, scopedActor, intent, catalog, "PREVIEW")).doesNotThrowAnyException();
	}

	@Test
	void inspect_whenScopedActorAggregatesAllTenantsOutsideScope_rejectsQuery() {
		// given
		var sql = "select domain, count(*) from service_catalog.mail_events group by domain";
		var intent = intent(BusinessIntentCategory.STATISTICS, Set.of(), Set.of(), "Resolved date range: none.");

		// when / then
		assertThatThrownBy(() -> guard.inspect(sql, scopedActor, intent, catalog, "PREVIEW"))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("scope");
	}

	private BusinessIntent intent(
			BusinessIntentCategory category,
			Set<String> emails,
			Set<String> domains,
			String dateRangeDescription) {
		return new BusinessIntent(
				category,
				emails,
				domains,
				dateRangeDescription,
				Set.of(),
				Set.of(),
				!emails.isEmpty(),
				true,
				"",
				Set.of(),
				Set.of());
	}

	private TableCatalog table(String name, long estimatedRows, TableRole role) {
		return new TableCatalog(
				"service_catalog",
				name,
				"TABLE",
				null,
				estimatedRows,
				List.of(
						column("email", ColumnSemanticType.EMAIL),
						column("domain", ColumnSemanticType.DOMAIN),
						column("created_at", ColumnSemanticType.DATETIME),
						column("status", ColumnSemanticType.STATUS),
						column("used_bytes", ColumnSemanticType.BYTE_SIZE),
						column("subject", ColumnSemanticType.UNKNOWN)),
				List.of(),
				List.of(),
				role,
				1.0,
				estimatedRows >= 0 ? "test" : "unavailable",
				true,
				List.of("email", "domain", "created_at", "status"));
	}

	private ColumnCatalog column(String name, ColumnSemanticType semanticType) {
		return new ColumnCatalog(name, "varchar", 255, true, null, semanticType == ColumnSemanticType.DOMAIN, semanticType, null, name);
	}
}
