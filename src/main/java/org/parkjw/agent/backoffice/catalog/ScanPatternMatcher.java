package org.parkjw.agent.backoffice.catalog;

import java.util.List;
import java.util.regex.Pattern;

import org.parkjw.agent.backoffice.config.AiQueryProperties;

import org.springframework.stereotype.Component;

@Component
public class ScanPatternMatcher {

	private static final List<String> BUILT_IN_EXCLUDES = List.of(
			"#mysql50#%", "information_schema", "mysql", "performance_schema", "sys");

	private final AiQueryProperties properties;

	public ScanPatternMatcher(AiQueryProperties properties) {
		this.properties = properties;
	}

	public boolean shouldScan(String databaseName) {
		var includes = properties.scan().includeDatabases();
		var excluded = matchesAny(databaseName, BUILT_IN_EXCLUDES)
				|| matchesAny(databaseName, properties.scan().excludeDatabases());
		var included = includes.isEmpty() || matchesAny(databaseName, includes);
		return included && !excluded;
	}

	public boolean shouldScan(String databaseName, CatalogScanRequest request) {
		if (!shouldScan(databaseName)) {
			return false;
		}
		if (request == null) {
			return true;
		}
		var targeted = request.targetDatabases().isEmpty()
				|| request.targetDatabases().stream().anyMatch(target -> target.equalsIgnoreCase(databaseName));
		var included = request.includeDatabases().isEmpty() || matchesAny(databaseName, request.includeDatabases());
		var excluded = matchesAny(databaseName, request.excludeDatabases());
		return targeted && included && !excluded;
	}

	private boolean matchesAny(String value, List<String> patterns) {
		return patterns.stream().anyMatch(pattern -> matches(value, pattern));
	}

	private boolean matches(String value, String pattern) {
		var regex = Pattern.quote(pattern)
				.replace("%", "\\E.*\\Q")
				.replace("_", "\\E.\\Q");
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value).matches();
	}
}
