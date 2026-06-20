package org.parkjw.agent.backoffice.query;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.CatalogSearchIndex;
import org.parkjw.agent.backoffice.security.AccessContext;
import org.parkjw.agent.backoffice.security.EffectiveAccessScope;

public record QueryContext(
		String action,
		QueryRequest request,
		EffectiveAccessScope effectiveScope,
		AccessContext accessContext,
		BusinessIntent intent,
		CatalogSnapshot catalog,
		CatalogSearchIndex catalogIndex,
		int limit) {
}
