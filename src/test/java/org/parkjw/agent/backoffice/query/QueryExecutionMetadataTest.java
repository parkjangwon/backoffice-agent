package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.parkjw.agent.backoffice.security.AccessRole;

import org.junit.jupiter.api.Test;

class QueryExecutionMetadataTest {

	@Test
	void constructor_whenCollectionsAreMutable_copiesEffectiveScopes() {
		// given
		var domains = new java.util.ArrayList<>(List.of("example.org"));
		var databases = new java.util.ArrayList<>(List.of("service_catalog"));

		// when
		var metadata = new QueryExecutionMetadata(
				"req-1",
				"PREVIEW",
				"SUCCESS",
				42,
				true,
				"catalog-v1",
				AccessRole.SCOPED,
				domains,
				databases,
				100,
				7,
				"sha256:abc123",
				Map.of("llmMs", 12L, "sqlExecutionMs", 8L));
		domains.add("mutated.example.org");
		databases.add("mutated_catalog");

		// then
		assertThat(metadata.requestId()).isEqualTo("req-1");
		assertThat(metadata.action()).isEqualTo("PREVIEW");
		assertThat(metadata.outcome()).isEqualTo("SUCCESS");
		assertThat(metadata.latencyMs()).isEqualTo(42);
		assertThat(metadata.cacheHit()).isTrue();
		assertThat(metadata.catalogVersion()).isEqualTo("catalog-v1");
		assertThat(metadata.effectiveRole()).isEqualTo(AccessRole.SCOPED);
		assertThat(metadata.effectiveScopeValues()).containsExactly("example.org");
		assertThat(metadata.effectiveDatabases()).containsExactly("service_catalog");
		assertThat(metadata.limit()).isEqualTo(100);
		assertThat(metadata.rowCount()).isEqualTo(7);
		assertThat(metadata.sqlHash()).isEqualTo("sha256:abc123");
		assertThat(metadata.stageLatencyMs()).containsEntry("llmMs", 12L);
	}

	@Test
	void constructor_whenScopesOrStageLatenciesAreNull_usesEmptyCollections() {
		// when
		var metadata = new QueryExecutionMetadata(
				"req-2",
				"EXPORT",
				"SUCCESS",
				99,
				false,
				null,
				AccessRole.GLOBAL,
				null,
				null,
				null,
				0,
				null,
				null);

		// then
		assertThat(metadata.effectiveScopeValues()).isEmpty();
		assertThat(metadata.effectiveDatabases()).isEmpty();
		assertThat(metadata.limit()).isNull();
		assertThat(metadata.sqlHash()).isNull();
		assertThat(metadata.stageLatencyMs()).isEmpty();
	}
}
