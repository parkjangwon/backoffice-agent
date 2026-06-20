package org.parkjw.agent.backoffice.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class CatalogBootstrapperTest {

	@Test
	void run_whenStoredSnapshotExists() {
		// given
		var repository = mock(CatalogRepository.class);
		var scanner = mock(SchemaScanner.class);
		var bootstrapper = new CatalogBootstrapper(repository, scanner);
		when(repository.hasStoredSnapshot()).thenReturn(true);

		// when
		bootstrapper.run(mock(ApplicationArguments.class));

		// then
		verify(scanner, never()).scan();
	}

	@Test
	void run_whenStoredSnapshotDoesNotExist() {
		// given
		var repository = mock(CatalogRepository.class);
		var scanner = mock(SchemaScanner.class);
		var bootstrapper = new CatalogBootstrapper(repository, scanner);
		var snapshot = new CatalogSnapshot(Instant.EPOCH, "product", "MySQL", "8.0", List.of(), List.of());
		when(repository.hasStoredSnapshot()).thenReturn(false);
		when(scanner.scan()).thenReturn(snapshot);
		when(repository.save(snapshot)).thenReturn(snapshot);

		// when
		bootstrapper.run(mock(ApplicationArguments.class));

		// then
		verify(repository).save(snapshot);
	}

	@Test
	void run_whenInitialScanFails() {
		// given
		var repository = mock(CatalogRepository.class);
		var scanner = mock(SchemaScanner.class);
		var bootstrapper = new CatalogBootstrapper(repository, scanner);
		when(repository.hasStoredSnapshot()).thenReturn(false);
		when(scanner.scan()).thenThrow(new IllegalStateException("database unavailable"));

		assertThatCode(() -> bootstrapper.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
	}
}
