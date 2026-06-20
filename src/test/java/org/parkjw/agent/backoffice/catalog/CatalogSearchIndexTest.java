package org.parkjw.agent.backoffice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CatalogSearchIndexTest {

	@Test
	void candidateTables_whenTokensAndDatabasesAreProvided_returnsScopedMatchesFirst() {
		// given
		var index = new CatalogSearchIndexBuilder().build(new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"product",
				"MySQL",
				"8.0",
				List.of(
						new DatabaseCatalog("tenant_a", "tenant", "a", List.of(
								table("mail_message", 20, "mail subject sender recipient sentTime"),
								table("account", 10, "email userUid"))),
						new DatabaseCatalog("tenant_b", "tenant", "b", List.of(
								table("mail_message", 30, "mail subject sender recipient sentTime")))),
				List.of(new ScopeMapping("a.example.org", "tenant_a", "master", "tenant_a"))));

		// when
		var candidates = index.candidateTables(Set.of("tenant_a"), Set.of("mail", "subject"));

		// then
		assertThat(candidates)
				.extracting(IndexedTable::qualifiedName)
				.containsExactly("tenant_a.mail_message");
	}

	@Test
	void scopeMappingsForValues_whenDomainIsKnown_returnsMappedDatabase() {
		// given
		var index = new CatalogSearchIndexBuilder().build(new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"product",
				"MySQL",
				"8.0",
				List.of(),
				List.of(new ScopeMapping("a.example.org", "tenant_a", "master", "tenant_a"))));

		// when
		var mappings = index.scopeMappingsForValues(List.of("A.EXAMPLE.ORG"));

		// then
		assertThat(mappings)
				.extracting(ScopeMapping::mappedDatabase)
				.containsExactly("tenant_a");
	}

	@Test
	void scopeRelationshipHints_whenCompanyScopedSchemaIsScanned_infersGenericScopeJoin() {
		// given
		var index = new CatalogSearchIndexBuilder().build(new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"product",
				"MySQL",
				"8.0",
				List.of(new DatabaseCatalog("service", "single", "a", List.of(
						table("company_master", 5, "companyUid companyName"),
						table("user_account", 100, "userUid companyUid emailAddress"),
						table("usage_event", 1000, "eventUid companyUid eventTime")))),
				List.of()));

		// when
		var hints = index.scopeRelationshipHints(Set.of("service"), Set.of("company", "usage"));

		// then
		assertThat(hints)
				.extracting(ScopeRelationshipHint::summary)
				.contains(
						"`service`.`user_account`.`companyUid` -> `service`.`company_master`.`companyUid` label `companyName`",
						"`service`.`usage_event`.`companyUid` -> `service`.`company_master`.`companyUid` label `companyName`");
	}

	private TableCatalog table(String name, long estimatedRows, String columns) {
		var columnCatalogs = java.util.Arrays.stream(columns.split(" "))
				.map(column -> new ColumnCatalog(column, "varchar", 255, true, null, column.toLowerCase().contains("domain")))
				.toList();
		return new TableCatalog("tenant", name, "TABLE", null, estimatedRows, columnCatalogs, List.of(), List.of());
	}
}
