package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SqlSafetyValidatorTest {

	private final SqlSafetyValidator validator = new SqlSafetyValidator(new SqlAnalyzer());

	@Test
	void requireReadOnlySelect_whenSqlIsSingleSelect() {
		// given
		var sql = "select id, email from users;";

		// when
		var result = validator.requireReadOnlySelect(sql);

		// then
		assertThat(result).isEqualTo("select id, email from users");
	}

	@Test
	void requireReadOnlySelect_whenSqlTriesUpdate() {
		// given
		var sql = "update users set status = 'ACTIVE'";

		// when / then
		assertThatThrownBy(() -> validator.requireReadOnlySelect(sql))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("SELECT");
	}

	@Test
	void requireReadOnlySelect_whenSqlHasMultipleStatements() {
		// given
		var sql = "select * from users; drop table users";

		// when / then
		assertThatThrownBy(() -> validator.requireReadOnlySelect(sql))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("single SELECT");
	}

	@Test
	void requireReadOnlySelect_whenSqlContainsCommentPayload() {
		// given
		var sql = "select * from users where domain = 'example.org' -- bypass";

		// when / then
		assertThatThrownBy(() -> validator.requireReadOnlySelect(sql))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("blocked token");
	}
}
