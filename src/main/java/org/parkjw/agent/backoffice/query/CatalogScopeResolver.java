package org.parkjw.agent.backoffice.query;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.security.AccessContext;

import org.springframework.stereotype.Component;

@Component
public class CatalogScopeResolver {

	public Set<String> targetDatabases(AccessContext accessContext, CatalogSnapshot catalog) {
		if (accessContext.globalAccess()) {
			return Set.of();
		}
		var mappedNames = new LinkedHashSet<String>();
		var sourceNames = new LinkedHashSet<String>();
		for (var scopeValue : accessContext.scopeValues()) {
			catalog.scopeMappings().stream()
					.filter(mapping -> mapping.scopeValue().equalsIgnoreCase(scopeValue))
					.forEach(mapping -> {
						mappedNames.add(mapping.mappedDatabase().toLowerCase(Locale.ROOT));
						if (mapping.sourceDatabase() != null && !mapping.sourceDatabase().isBlank()) {
							sourceNames.add(mapping.sourceDatabase().toLowerCase(Locale.ROOT));
						}
					});
		}
		if (!mappedNames.isEmpty()) {
			return mappedNames;
		}
		if (!sourceNames.isEmpty()) {
			return sourceNames;
		}
		return accessContext.allowedDatabases().stream()
				.map(value -> value.toLowerCase(Locale.ROOT))
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
	}
}
