package org.parkjw.agent.backoffice.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.parkjw.agent.backoffice.config.AiQueryProperties;

import org.junit.jupiter.api.Test;

class CatalogControllerTest {

	private final SchemaScanner scanner = mock(SchemaScanner.class);
	private final CatalogRepository repository = mock(CatalogRepository.class);
	private final AiQueryProperties properties = new AiQueryProperties(
			null,
			new AiQueryProperties.Catalog("/var/private/catalog/catalog-snapshot.json"),
			null,
				new AiQueryProperties.Scan(List.of("tenant_%"), List.of("tenant_tmp_%")),
				null,
				null,
				null);
	private final CatalogController controller = new CatalogController(scanner, repository, properties);

	@Test
	void status_whenSnapshotExists_returnsSafeCatalogSummary() {
		// given
		var snapshot = snapshot();
		when(repository.current()).thenReturn(snapshot);
		when(repository.hasStoredSnapshot()).thenReturn(true);

		// when
		var response = controller.status();

		// then
		assertThat(response.success()).isTrue();
		assertThat(response.data()).isEqualTo(new CatalogStatusResponse(
				true,
				Instant.parse("2026-06-19T01:02:03Z"),
				"catalog-v2",
				2,
				3,
				3,
				2,
				1,
				List.of("tenant_%"),
				List.of("tenant_tmp_%"),
				123,
				"catalog-snapshot.json"));
	}

	@Test
	void scan_whenRequestBodyIsMissing_usesExistingFullScanBehavior() {
		// given
		var snapshot = snapshot();
		when(scanner.scan()).thenReturn(snapshot);
		when(repository.save(snapshot)).thenReturn(snapshot);

		// when
		var response = controller.scan(null);

		// then
		assertThat(response.data().catalogVersion()).isEqualTo("catalog-v2");
		assertThat(response.data().databaseCount()).isEqualTo(2);
		assertThat(response.data().tableCount()).isEqualTo(3);
		verify(scanner).scan();
	}

	@Test
	void scan_whenRequestBodyTargetsDatabases_passesTargetedScanRequest() {
		// given
		var request = new CatalogScanRequest(
				List.of("tenant_alpha"),
				List.of("tenant_%"),
				List.of("tenant_tmp_%"));
		var snapshot = snapshot();
		when(scanner.scan(request)).thenReturn(snapshot);
		when(repository.save(snapshot)).thenReturn(snapshot);

		// when
		var response = controller.scan(request);

		// then
		assertThat(response.data().catalogVersion()).isEqualTo("catalog-v2");
		assertThat(response.data().databaseCount()).isEqualTo(2);
		assertThat(response.data().tableCount()).isEqualTo(3);
		verify(scanner).scan(request);
	}

	private CatalogSnapshot snapshot() {
		var account = new TableCatalog(
				"tenant_alpha",
				"account",
				"BASE TABLE",
				"Accounts",
				0L,
				List.of(new ColumnCatalog("id", "bigint", 20, false, "Identifier", false)),
				List.of("id"),
				List.of("idx_account_id"));
		var message = new TableCatalog(
				"tenant_alpha",
				"message",
				"BASE TABLE",
				"Messages",
				42L,
				List.of(new ColumnCatalog("subject", "varchar", 255, true, "Subject", false)),
				List.of(),
				List.of());
		var audit = new TableCatalog(
				"tenant_beta",
				"audit_log",
				"BASE TABLE",
				"Audit log",
				-1L,
				List.of(new ColumnCatalog("created_at", "timestamp", 0, false, "Created", false)),
				List.of(),
				List.of("idx_audit_created_at"));
		return new CatalogSnapshot(
				Instant.parse("2026-06-19T01:02:03Z"),
				"backoffice-agent",
				"MySQL",
				"8.0",
				List.of(
						new DatabaseCatalog("tenant_alpha", "scope_candidate", "abc", List.of(account, message)),
						new DatabaseCatalog("tenant_beta", "scope_candidate", "def", List.of(audit))),
				List.of(),
				new CatalogScanMetadata(
						"catalog-v2",
						123,
						List.of("tenant_alpha", "tenant_beta"),
						List.of("tenant_tmp_001"),
						List.of("tenant_alpha", "tenant_beta"),
						2,
						3));
	}
}
