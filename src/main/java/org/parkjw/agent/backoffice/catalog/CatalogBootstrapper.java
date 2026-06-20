package org.parkjw.agent.backoffice.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CatalogBootstrapper implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CatalogBootstrapper.class);

	private final CatalogRepository repository;
	private final SchemaScanner scanner;

	public CatalogBootstrapper(CatalogRepository repository, SchemaScanner scanner) {
		this.repository = repository;
		this.scanner = scanner;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (repository.hasStoredSnapshot()) {
			log.info("catalog bootstrap skipped reason=existing-snapshot");
			return;
		}

		try {
			log.info("catalog snapshot missing. starting initial schema scan");
			var snapshot = repository.save(scanner.scan());
			log.info(
					"catalog snapshot initialized databaseCount={} scopeMappingCount={}",
					snapshot.databases().size(),
					snapshot.scopeMappings().size());
		}
		catch (RuntimeException exception) {
			log.warn(
					"catalog snapshot initialization failed. server will continue with empty catalog. reason={}",
					exception.getMessage(),
					exception);
		}
	}
}
