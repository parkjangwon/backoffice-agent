package org.parkjw.agent.backoffice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.parkjw.agent.backoffice.config.AiQueryProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import tools.jackson.databind.json.JsonMapper;

class CatalogRepositoryTest {

	@TempDir
	Path tempDir;

	@Test
	void save_whenSnapshotIsScanned() {
		// given
		var storagePath = tempDir.resolve("catalog.json");
		var properties = new AiQueryProperties(
				null,
				new AiQueryProperties.Catalog(storagePath.toString()),
				null,
					null,
					null,
					null,
					null);
		var repository = repository(properties);
		var snapshot = new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"product",
				"MySQL",
				"8.0",
				List.of(new DatabaseCatalog("service_catalog", "scope_candidate", "abc", List.of())),
				List.of(new ScopeMapping("example.org", "tenant_example", "service_catalog", "tenant_example")));

		// when
		repository.save(snapshot);
		var reloaded = repository(properties).current();

		// then
		assertThat(storagePath).exists();
		assertThat(repository.hasStoredSnapshot()).isTrue();
		assertThat(reloaded.databases()).extracting(DatabaseCatalog::name).containsExactly("service_catalog");
	}

	@Test
	void currentIndex_whenSnapshotIsSavedAndReloaded_containsIndexedTables() {
		// given
		var storagePath = tempDir.resolve("catalog-index.json");
		var properties = new AiQueryProperties(
				null,
				new AiQueryProperties.Catalog(storagePath.toString()),
				null,
					null,
					null,
					null,
					null);
		var repository = repository(properties);
		var snapshot = new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"product",
				"MySQL",
				"8.0",
				List.of(new DatabaseCatalog("tenant_example", "tenant", "abc", List.of(
						new TableCatalog(
								"tenant_example",
								"mail_message",
								"TABLE",
								null,
								10L,
								List.of(new ColumnCatalog("subject", "varchar", 255, true, null, false)),
								List.of(),
								List.of())))),
				List.of());

		// when
		repository.save(snapshot);
		var reloaded = repository(properties);

		// then
		assertThat(reloaded.currentIndex().candidateTables(Set.of("tenant_example"), Set.of("subject")))
				.extracting(IndexedTable::qualifiedName)
				.containsExactly("tenant_example.mail_message");
	}

	@Test
	void hasStoredSnapshot_whenSnapshotFileDoesNotExist() {
		// given
		var storagePath = tempDir.resolve("missing-catalog.json");
		var properties = new AiQueryProperties(
				null,
				new AiQueryProperties.Catalog(storagePath.toString()),
				null,
					null,
					null,
					null,
					null);
		var repository = repository(properties);

		// when
		var exists = repository.hasStoredSnapshot();

		// then
		assertThat(exists).isFalse();
	}

	@Test
	void current_whenLegacySnapshotJsonHasNoMetadata_usesStableDefaults() throws Exception {
		// given
		var storagePath = tempDir.resolve("legacy-catalog.json");
		Files.writeString(storagePath, """
				{
				  "scannedAt": "2026-06-19T00:00:00Z",
				  "productName": "legacy",
				  "databaseProductName": "MySQL",
				  "databaseProductVersion": "8.0",
				  "databases": [
				    {
				      "name": "tenant_example",
				      "role": "tenant",
				      "fingerprint": "abc",
				      "tables": [
				        {
				          "databaseName": "tenant_example",
				          "name": "account",
				          "type": "BASE TABLE",
				          "remarks": "Domain accounts",
				          "estimatedRows": 42,
				          "columns": [
				            {
				              "name": "email",
				              "typeName": "varchar",
				              "size": 255,
				              "nullable": false,
				              "remarks": "Email address",
				              "scopeCandidate": false
				            }
				          ],
				          "primaryKeys": ["id"],
				          "indexes": ["idx_email"]
				        }
				      ]
				    }
				  ],
				  "scopeMappings": []
				}
				""");
		var properties = new AiQueryProperties(
				null,
				new AiQueryProperties.Catalog(storagePath.toString()),
				null,
					null,
					null,
					null,
					null);

		// when
		var snapshot = repository(properties).current();

		// then
		assertThat(snapshot.metadata().catalogVersion()).isEqualTo(CatalogScanMetadata.CATALOG_VERSION);
		assertThat(snapshot.metadata().scannedDatabaseCount()).isEqualTo(1);
		assertThat(snapshot.metadata().scannedTableCount()).isEqualTo(1);
		assertThat(snapshot.databases().getFirst().tables().getFirst().role()).isEqualTo(TableRole.ACCOUNT);
		assertThat(snapshot.databases().getFirst().tables().getFirst().rowEstimateSource()).isEqualTo("snapshot_default");
		assertThat(snapshot.databases().getFirst().tables().getFirst().indexed()).isTrue();
		assertThat(snapshot.databases().getFirst().tables().getFirst().columns().getFirst().label()).isEqualTo("Email address");
		assertThat(snapshot.databases().getFirst().tables().getFirst().columns().getFirst().semanticConfidence()).isGreaterThan(0.7);
	}

	private JsonMapper objectMapper() {
		return JsonMapper.builder()
				.build();
	}

	private CatalogRepository repository(AiQueryProperties properties) {
		return new CatalogRepository(objectMapper(), properties, new CatalogSearchIndexBuilder());
	}
}
