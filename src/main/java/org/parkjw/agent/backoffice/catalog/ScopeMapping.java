package org.parkjw.agent.backoffice.catalog;

public record ScopeMapping(
		String scopeValue,
		String mappedScope,
		String sourceDatabase,
		String mappedDatabase) {
}
