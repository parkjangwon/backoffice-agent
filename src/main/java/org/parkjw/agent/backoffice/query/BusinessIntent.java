package org.parkjw.agent.backoffice.query;

import java.util.Set;

public record BusinessIntent(
		BusinessIntentCategory category,
		Set<String> emails,
		Set<String> mentionedDomains,
		String dateRangeDescription,
		Set<String> metricHints,
		Set<String> orderHints,
		boolean userScoped,
		boolean safeForBusinessQuery,
		String noResultGuidanceSeed,
		Set<String> catalogTokens,
		Set<String> requestedYearMonths) {

	public BusinessIntent {
		emails = Set.copyOf(emails);
		mentionedDomains = Set.copyOf(mentionedDomains);
		metricHints = Set.copyOf(metricHints);
		orderHints = Set.copyOf(orderHints);
		catalogTokens = Set.copyOf(catalogTokens);
		requestedYearMonths = Set.copyOf(requestedYearMonths);
	}

	boolean hasExactEmail() {
		return !emails.isEmpty();
	}
}
