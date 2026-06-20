package org.parkjw.agent.backoffice.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.parkjw.agent.backoffice.config.AiQueryProperties;
import org.parkjw.agent.backoffice.query.QueryExecutionMetadata;
import org.parkjw.agent.backoffice.query.QueryRequest;
import org.parkjw.agent.backoffice.security.AccessRole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

class AuditServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void record_whenMetadataIsProvided_appendsAndLoadsSafeJsonl() throws Exception {
		// given
		var storagePath = tempDir.resolve("audit.jsonl");
		var properties = new AiQueryProperties(
					null,
					null,
					new AiQueryProperties.Audit(storagePath.toString()),
					null,
					null,
					null,
					null);
		var service = new AuditService(objectMapper(), properties);
		var request = new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				"로그인 이력",
				null,
				10);
		var metadata = metadata(0);

		// when
		service.record(metadata, request);
		var reloaded = new AuditService(objectMapper(), properties).recent();

		// then
		assertThat(storagePath).exists();
		assertThat(reloaded).hasSize(1);
		var event = reloaded.getFirst();
		assertThat(event.promptHash()).startsWith("sha256:");
		assertThat(event.sqlHash()).isEqualTo("sha256:abc123");
		assertThat(event.action()).isEqualTo("PREVIEW");
		assertThat(event.rowCount()).isEqualTo(0);
		assertThat(Files.readString(storagePath))
				.contains("\"sqlHash\"")
				.contains("\"promptHash\"")
				.doesNotContain("select * from message_events")
				.doesNotContain("로그인 이력")
				.doesNotContain("\"sql\"");
	}

	@Test
	void load_whenLegacyJsonlContainsSqlField_dropsRawSqlAndKeepsMetadata() throws Exception {
		// given
		var storagePath = tempDir.resolve("legacy-audit.jsonl");
		Files.writeString(storagePath, """
				{"occurredAt":"2026-06-19T00:00:00Z","actorId":"operator-123","role":"SCOPED","prompt":"legacy","sql":"select * from secret_table","rowCount":3,"action":"PREVIEW"}
				""");
		var properties = new AiQueryProperties(
				null,
				null,
				new AiQueryProperties.Audit(storagePath.toString()),
				null,
				null,
				null,
				null);

		// when
		var reloaded = new AuditService(objectMapper(), properties).recent();

		// then
		assertThat(reloaded).hasSize(1);
		var event = reloaded.getFirst();
		assertThat(event.promptHash()).startsWith("sha256:");
		assertThat(event.rowCount()).isEqualTo(3);
		assertThat(event.sqlHash()).isNull();
		assertThat(event.toString()).doesNotContain("select * from secret_table");
		assertThat(objectMapper().writeValueAsString(event))
				.doesNotContain("select * from secret_table")
				.doesNotContain("legacy")
				.doesNotContain("\"prompt\"")
				.doesNotContain("\"sql\"");
		assertThat(Files.readString(storagePath))
				.contains("\"promptHash\"")
				.doesNotContain("select * from secret_table")
				.doesNotContain("legacy")
				.doesNotContain("\"prompt\"")
				.doesNotContain("\"sql\"");
	}

	@Test
	void eventShape_whenSerializedAndStringified_neverExposesRawSqlOrPrompt() throws Exception {
		// given
		var event = new AuditEvent(
				java.time.Instant.parse("2026-06-19T00:00:00Z"),
				"operator-123",
				AccessRole.GLOBAL,
				"sha256:prompt123",
				"sha256:def456",
				12,
				"EXPORT",
				"req-2",
				"SUCCESS",
				31,
				true,
				"catalog-v2",
				AccessRole.GLOBAL,
				List.of("example.org"),
				List.of("service_catalog"),
				50);

		// when
		var serialized = objectMapper().writeValueAsString(event);

		// then
		assertThat(serialized)
				.contains("\"sqlHash\"")
				.contains("\"promptHash\"")
				.doesNotContain("\"prompt\"")
				.doesNotContain("\"sql\"")
				.doesNotContain("active users")
				.doesNotContain("select ");
		assertThat(event.toString())
				.contains("sqlHash=sha256:def456")
				.doesNotContain("active users")
				.doesNotContain("select ");
	}

	private QueryExecutionMetadata metadata(int rowCount) {
		return new QueryExecutionMetadata(
				"req-1",
				"PREVIEW",
				"SUCCESS",
				25,
				false,
				"catalog-v1",
				AccessRole.SCOPED,
				List.of("example.org"),
				List.of("service_catalog"),
				10,
				rowCount,
				"sha256:abc123");
	}

	private JsonMapper objectMapper() {
		return JsonMapper.builder()
				.build();
	}
}
