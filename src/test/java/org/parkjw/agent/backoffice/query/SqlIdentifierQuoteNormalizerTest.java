package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlIdentifierQuoteNormalizerTest {

	@Test
	void normalize_whenQualifiedIdentifiersUseDoubleQuotes_convertsThemToBackticks() {
		// given
		var sql = "select q.emailId as \"email\" from \"tenant_acme\".\"usage_quota\" q";

		// when
		var normalized = SqlIdentifierQuoteNormalizer.normalize(sql);

		// then
		assertThat(normalized)
				.contains("from `tenant_acme`.`usage_quota` q")
				.contains("as `email`");
	}
}
