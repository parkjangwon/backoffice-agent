package org.parkjw.agent.backoffice.catalog;

import java.util.List;

public record CatalogScanRequest(
		List<String> targetDatabases,
		List<String> includeDatabases,
		List<String> excludeDatabases) {

	public CatalogScanRequest {
		targetDatabases = targetDatabases == null ? List.of() : List.copyOf(targetDatabases);
		includeDatabases = includeDatabases == null ? List.of() : List.copyOf(includeDatabases);
		excludeDatabases = excludeDatabases == null ? List.of() : List.copyOf(excludeDatabases);
	}
}
