package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

	@Test
	void normalize_whenSingleDatabaseIsAllowed_replacesPlaceholderDatabaseQualifier() {
		// given
		var sql = "select count(*) as total from database.mbbmaillog202606";

		// when
		var normalized = SqlIdentifierQuoteNormalizer.normalize(sql, List.of("arch"));

		// then
		assertThat(normalized).isEqualTo("select count(*) as total from `arch`.`mbbmaillog202606`");
	}

	@Test
	void normalize_whenMultipleDatabasesAreAllowed_keepsPlaceholderDatabaseQualifier() {
		// given
		var sql = "select count(*) as total from database.mbbmaillog202606";

		// when
		var normalized = SqlIdentifierQuoteNormalizer.normalize(sql, List.of("arch", "tenant_a"));

		// then
		assertThat(normalized).isEqualTo(sql);
	}
}
