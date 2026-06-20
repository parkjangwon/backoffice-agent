package org.parkjw.agent.backoffice.audit;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.parkjw.agent.backoffice.common.LogFingerprints;
import org.parkjw.agent.backoffice.config.AiQueryProperties;
import org.parkjw.agent.backoffice.query.QueryExecutionMetadata;
import org.parkjw.agent.backoffice.query.QueryRequest;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AuditService {

	private final ObjectMapper objectMapper;
	private final Path storagePath;
	private final List<AuditEvent> events = new ArrayList<>();

	public AuditService(ObjectMapper objectMapper, AiQueryProperties properties) {
		this.objectMapper = objectMapper;
		this.storagePath = Path.of(properties.audit().storagePath());
		events.addAll(load());
	}

	public synchronized void record(QueryExecutionMetadata metadata, QueryRequest request) {
		var event = new AuditEvent(
				Instant.now(),
				request.actorId(),
				request.role(),
				LogFingerprints.sha256(request.prompt()),
				metadata.sqlHash(),
				metadata.rowCount(),
				metadata.action(),
				metadata.requestId(),
				metadata.outcome(),
				metadata.latencyMs(),
				metadata.cacheHit(),
				metadata.catalogVersion(),
				metadata.effectiveRole(),
				metadata.effectiveScopeValues(),
				metadata.effectiveDatabases(),
				metadata.limit(),
				metadata.stageLatencyMs());
		events.add(event);
		append(event);
	}

	public synchronized List<AuditEvent> recent() {
		return List.copyOf(events.reversed());
	}

	private List<AuditEvent> load() {
		if (!Files.exists(storagePath)) {
			return List.of();
		}
		try {
			return scrubbedLines().stream()
					.map(this::readEvent)
					.toList();
		}
		catch (IOException exception) {
			throw new AuditStorageException("Failed to load audit events from " + storagePath, exception);
		}
	}

	private List<String> scrubbedLines() throws IOException {
		var original = Files.readAllLines(storagePath, StandardCharsets.UTF_8).stream()
				.filter(line -> !line.isBlank())
				.toList();
		var scrubbed = original.stream()
				.map(this::scrubLine)
				.toList();
		if (!scrubbed.equals(original)) {
			replaceAuditFile(scrubbed);
		}
		return scrubbed;
	}

	private void replaceAuditFile(List<String> scrubbed) throws IOException {
		var target = storagePath.toAbsolutePath();
		var parent = target.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		var tempFile = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
		try {
			Files.write(tempFile, scrubbed, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
			try (var channel = FileChannel.open(tempFile, StandardOpenOption.READ)) {
				channel.force(true);
			}
			try {
				Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException exception) {
				Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException | RuntimeException exception) {
			Files.deleteIfExists(tempFile);
			throw exception;
		}
	}

	private String scrubLine(String line) {
		try {
			var node = objectMapper.readTree(line);
			if (node instanceof ObjectNode objectNode) {
				scrubNode(objectNode);
				return objectMapper.writeValueAsString(objectNode);
			}
			return line;
		}
		catch (RuntimeException exception) {
			throw new AuditStorageException("Failed to parse audit event.", exception);
		}
	}

	private AuditEvent readEvent(String line) {
		try {
			var node = objectMapper.readTree(line);
			if (node instanceof ObjectNode objectNode) {
				scrubNode(objectNode);
				return objectMapper.treeToValue(objectNode, AuditEvent.class);
			}
			return objectMapper.treeToValue(node, AuditEvent.class);
		}
		catch (RuntimeException exception) {
			throw new AuditStorageException("Failed to parse audit event.", exception);
		}
	}

	private void scrubNode(ObjectNode objectNode) {
		objectNode.remove("sql");
		if (!objectNode.has("promptHash") && objectNode.has("prompt")) {
			objectNode.put("promptHash", LogFingerprints.sha256(objectNode.get("prompt").asString()));
		}
		objectNode.remove("prompt");
		if (!objectNode.has("rowCount")) {
			objectNode.put("rowCount", 0);
		}
		if (!objectNode.has("latencyMs")) {
			objectNode.put("latencyMs", 0);
		}
		if (!objectNode.has("cacheHit")) {
			objectNode.put("cacheHit", false);
		}
		if (!objectNode.has("stageLatencyMs")) {
			objectNode.putObject("stageLatencyMs");
		}
	}

	private void append(AuditEvent event) {
		try {
			var parent = storagePath.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(
					storagePath,
					objectMapper.writeValueAsString(event) + System.lineSeparator(),
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		}
		catch (IOException exception) {
			throw new AuditStorageException("Failed to append audit event to " + storagePath, exception);
		}
		catch (RuntimeException exception) {
			throw new AuditStorageException("Failed to serialize audit event.", exception);
		}
	}

}
