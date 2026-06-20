package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.CatalogSearchIndexBuilder;
import org.parkjw.agent.backoffice.catalog.ColumnCatalog;
import org.parkjw.agent.backoffice.catalog.DatabaseCatalog;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.catalog.ScopeMapping;
import org.parkjw.agent.backoffice.security.AccessContext;
import org.parkjw.agent.backoffice.security.AccessRole;

import org.junit.jupiter.api.Test;

class CatalogSummaryBuilderTest {

	@Test
	void catalogSummary_whenExactEmailRequest_prefersNonEmptyUserScopedEventTable() {
		// given
		var intentParser = new BusinessIntentParser(new PromptDateRangeExtractor());
		var builder = new CatalogSummaryBuilder(new CatalogScopeResolver());
		var request = new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				"user@example.org 사용자의 2026년 5월 서비스 이용 이벤트 로그",
				null,
				10);
		var accessContext = new AccessContext(
				request.actorId(),
				request.role(),
				List.of("example.org"),
				List.of(),
				true);
		var catalog = new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"test",
				"MySQL",
				"8",
				List.of(new DatabaseCatalog("tenant_example", "tenant", "abc", List.of(
						table("transport_event_202604", 100L, "eventId", "domainName", "sender", "recipient", "insertDate"),
						table("transport_event_202605", 0L, "eventId", "domainName", "sender", "recipient", "insertDate"),
						table("user_service_event", 4000L, "userUid", "domainUid", "eventId", "adrFrom", "adrTo", "eventSize", "timeMillis"),
						table("account", 50L, "userUid", "domainUid", "emailId")))),
				List.of());

		// when
		var summary = builder.build(accessContext, intentParser.parse(request.prompt()), new CatalogSearchIndexBuilder().build(catalog));

		// then
		assertThat(summary).contains("- user_service_event");
		assertThat(summary).doesNotContain("- transport_event_202605");
		assertThat(summary).doesNotContain("- transport_event_202604");
	}

	@Test
	void catalogSummary_whenScopeMappingExists_prefersMappedDatabaseOverSourceDatabase() {
		// given
		var intentParser = new BusinessIntentParser(new PromptDateRangeExtractor());
		var builder = new CatalogSummaryBuilder(new CatalogScopeResolver());
		var request = new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				"user@example.org 사용자의 서비스 이용 이벤트 로그",
				null,
				10);
		var accessContext = new AccessContext(
				request.actorId(),
				request.role(),
				List.of("example.org"),
				List.of(),
				true);
		var catalog = new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"test",
				"MySQL",
				"8",
				List.of(
						new DatabaseCatalog("master_catalog", "tenant", "abc", List.of(
								table("source_event_202605", 100L, "domainName", "sender", "recipient", "insertDate"))),
						new DatabaseCatalog("tenant_example", "tenant", "abc", List.of(
								table("tenant_event", 4000L, "userUid", "domainUid", "eventId", "adrFrom", "adrTo", "timeMillis")))),
				List.of(new ScopeMapping("example.org", "tenant_example", "master_catalog", "tenant_example")));

		// when
		var summary = builder.build(accessContext, intentParser.parse(request.prompt()), new CatalogSearchIndexBuilder().build(catalog));

		// then
		assertThat(summary).contains("database tenant_example");
		assertThat(summary).doesNotContain("database master_catalog");
	}

	@Test
	void catalogSummary_whenGenericScopeRelationshipExists_includesScopeJoinHints() {
		// given
		var intentParser = new BusinessIntentParser(new PromptDateRangeExtractor());
		var builder = new CatalogSummaryBuilder(new CatalogScopeResolver());
		var request = new QueryRequest(
				"root@example.org",
				AccessRole.GLOBAL,
				"company 기준 usage event 조회",
				null,
				10);
		var accessContext = new AccessContext(
				request.actorId(),
				request.role(),
				List.of(),
				List.of(),
				true);
		var catalog = new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"test",
				"MySQL",
				"8",
				List.of(new DatabaseCatalog("service", "single", "abc", List.of(
						table("company_master", 5L, "companyUid", "companyName"),
						table("usage_event", 4000L, "eventUid", "companyUid", "eventTime")))),
				List.of());

		// when
		var summary = builder.build(accessContext, intentParser.parse(request.prompt()), new CatalogSearchIndexBuilder().build(catalog));

		// then
		assertThat(summary)
				.contains("Scope relationship hints")
				.contains("`service`.`usage_event`.`companyUid` -> `service`.`company_master`.`companyUid` label `companyName`");
	}

	private TableCatalog table(String name, long estimatedRows, String... columns) {
		return new TableCatalog(
				"tenant_example",
				name,
				"TABLE",
				null,
				estimatedRows,
				List.of(columns).stream()
						.map(column -> new ColumnCatalog(column, "varchar", 255, true, null, column.toLowerCase().contains("domain")))
						.toList(),
				List.of(),
				List.of());
	}
}
