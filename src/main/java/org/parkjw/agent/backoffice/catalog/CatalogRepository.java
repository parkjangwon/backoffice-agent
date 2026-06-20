package org.parkjw.agent.backoffice.catalog;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

import org.parkjw.agent.backoffice.config.AiQueryProperties;

import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

@Repository
public class CatalogRepository {

	private final ObjectMapper objectMapper;
	private final CatalogSearchIndexBuilder indexBuilder;
	private final Path storagePath;
	private final AtomicReference<CatalogSnapshot> current = new AtomicReference<>(CatalogSnapshot.empty());
	private final AtomicReference<CatalogSearchIndex> currentIndex = new AtomicReference<>(CatalogSearchIndex.empty());

	public CatalogRepository(ObjectMapper objectMapper, AiQueryProperties properties, CatalogSearchIndexBuilder indexBuilder) {
		this.objectMapper = objectMapper;
		this.indexBuilder = indexBuilder;
		this.storagePath = Path.of(properties.catalog().storagePath());
		setCurrent(load());
	}

	public CatalogSnapshot current() {
		return current.get();
	}

	public CatalogSearchIndex currentIndex() {
		return currentIndex.get();
	}

	public boolean hasStoredSnapshot() {
		return Files.exists(storagePath);
	}

	public CatalogSnapshot save(CatalogSnapshot snapshot) {
		write(snapshot);
		setCurrent(snapshot);
		return snapshot;
	}

	private void setCurrent(CatalogSnapshot snapshot) {
		current.set(snapshot);
		currentIndex.set(indexBuilder.build(snapshot));
	}

	private CatalogSnapshot load() {
		if (!Files.exists(storagePath)) {
			return CatalogSnapshot.empty();
		}
		try {
			return objectMapper.readValue(storagePath.toFile(), CatalogSnapshot.class);
		}
		catch (RuntimeException exception) {
			throw new CatalogStorageException("Failed to load catalog snapshot from " + storagePath, exception);
		}
	}

	private void write(CatalogSnapshot snapshot) {
		try {
			var parent = storagePath.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			var tempPath = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), snapshot);
			move(tempPath);
		}
		catch (IOException exception) {
			throw new CatalogStorageException("Failed to write catalog snapshot to " + storagePath, exception);
		}
		catch (RuntimeException exception) {
			throw new CatalogStorageException("Failed to serialize catalog snapshot to " + storagePath, exception);
		}
	}

	private void move(Path tempPath) throws IOException {
		try {
			Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException exception) {
			Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
