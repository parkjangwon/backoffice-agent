package org.parkjw.agent.backoffice.catalog;

import java.util.List;

public record DatabaseCatalog(
		String name,
		String role,
		String fingerprint,
		List<TableCatalog> tables) {
}
