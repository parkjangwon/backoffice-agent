package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.ColumnCatalog;
import org.parkjw.agent.backoffice.catalog.DatabaseCatalog;
import org.parkjw.agent.backoffice.catalog.TableCatalog;

import org.junit.jupiter.api.Test;

class ResultPresentationServiceTest {

	private final ResultPresentationService service = new ResultPresentationService(new HumanReadableValueFormatter());
	private final NoResultGuidanceService noResultGuidanceService = new NoResultGuidanceService();
	private final QueryResultViewMapper viewMapper = new QueryResultViewMapper(noResultGuidanceService);

	@Test
	void present_whenByteSizeColumnExists_addsFormattedTextColumn() {
		// given
		var row = new LinkedHashMap<String, Object>();
		row.put("email", "user@example.org");
		row.put("mailUsedSize", 1_258_072_482L);
		var result = new QueryResult(
				"select email, mailUsedSize from quota",
				List.of("email", "mailUsedSize"),
				List.of(row),
				1);

		// when
		var presented = service.present(result, catalog());

		// then
		assertThat(presented.columns()).containsExactly("email", "mailUsedSize", "mailUsedSizeText");
		assertThat(presented.rows().getFirst()).containsEntry("mailUsedSize", 1_258_072_482L);
		assertThat(presented.rows().getFirst()).containsEntry("mailUsedSizeText", "1.17 GB");
	}

	@Test
	void textFormatter_whenFormattedColumnExists_usesFormattedValue() {
		// given
		var row = new LinkedHashMap<String, Object>();
		row.put("email", "user@example.org");
		row.put("mailUsedSize", 1_258_072_482L);
		var presented = service.present(new QueryResult(
				"select email, mailUsedSize from quota",
				List.of("email", "mailUsedSize"),
				List.of(row),
				1), catalog());

		// when
		var text = new NaturalLanguageResultFormatter(viewMapper, noResultGuidanceService).format(presented);

		// then
		assertThat(text).contains("메일함 사용량: 1.17 GB");
		assertThat(text).doesNotContain("mailUsedSize");
		assertThat(text).doesNotContain("1258072482");
	}

	@Test
	void textFormatter_whenDomainIsOnlyScopeColumn_hidesDomainAndUsesBusinessLabel() {
		var row = new LinkedHashMap<String, Object>();
		row.put("subject", "가산역 예쁜돼지 회식 초대");
		row.put("domain", "acme");
		var result = new QueryResult(
				"select subject, domain from mail",
				List.of("subject", "domain"),
				List.of(row),
				1);

		var text = new NaturalLanguageResultFormatter(viewMapper, noResultGuidanceService).format(result);

		assertThat(text).contains("- 가산역 예쁜돼지 회식 초대");
		assertThat(text).doesNotContain("subject");
		assertThat(text).doesNotContain("domain");
		assertThat(text).doesNotContain("acme");
	}

	@Test
	void responseMapper_whenQueryResultHasSql_returnsUserFacingDataOnly() {
		var row = new LinkedHashMap<String, Object>();
		row.put("subject", "가산역 예쁜돼지 회식 초대");
		row.put("domain", "acme");
		var result = new QueryResult(
				"select subject, domain from mail",
				List.of("subject", "domain"),
				List.of(row),
				1);

		var response = viewMapper.toResponse(result);

		assertThat(response.rowCount()).isEqualTo(1);
		assertThat(response.columns()).containsExactly("메일 제목");
		assertThat(response.rows().getFirst()).containsEntry("메일 제목", "가산역 예쁜돼지 회식 초대");
		assertThat(response.toString()).doesNotContain("select subject");
		assertThat(response.toString()).doesNotContain("acme");
	}

	@Test
	void responseMapper_whenQueryResultHasNoRows_returnsSafeMessageAndGuidance() {
		var result = new QueryResult(
				"select domain, tenant_id from tenant_example.mail_hidden_scope",
				List.of("domain", "tenant_id"),
				List.of(),
				0);

		var response = viewMapper.toResponse(result);

		assertThat(response.rowCount()).isZero();
		assertThat(response.columns()).isEmpty();
		assertThat(response.rows()).isEmpty();
		assertThat(response.message()).isEqualTo("조건에 맞는 조회 결과가 없습니다.");
		assertThat(response.guidance())
				.contains("실제 데이터가 없거나")
				.contains("조건이 너무 좁게 해석되었을 수 있습니다")
				.contains("표현을 바꿔 다시 요청해 주세요");
		assertThat(response.toString())
				.doesNotContain("select")
				.doesNotContain("tenant_example")
				.doesNotContain("mail_hidden_scope")
				.doesNotContain("tenant_id")
				.doesNotContain("domain");
	}

	@Test
	void textFormatter_whenQueryResultHasNoRows_returnsKoreanGuidanceWithoutInternals() {
		var result = new QueryResult(
				"select domain, tenant_id from tenant_example.mail_hidden_scope",
				List.of("domain", "tenant_id"),
				List.of(),
				0);

		var text = new NaturalLanguageResultFormatter(viewMapper, noResultGuidanceService).format(result);

		assertThat(text).contains("조건에 맞는 조회 결과가 없습니다.");
		assertThat(text)
				.contains("실제 데이터가 없거나")
				.contains("조건이 너무 좁게 해석되었을 수 있습니다")
				.contains("표현을 바꿔 다시 요청해 주세요");
		assertThat(text).isNotEqualTo("조회 결과가 없습니다.");
		assertThat(text)
				.doesNotContain("select")
				.doesNotContain("tenant_example")
				.doesNotContain("mail_hidden_scope")
				.doesNotContain("tenant_id")
				.doesNotContain("domain");
	}

	private CatalogSnapshot catalog() {
		return new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"test",
				"MySQL",
				"8",
				List.of(new DatabaseCatalog("tenant_example", "tenant", "abc", List.of(
						new TableCatalog(
								"tenant_example",
								"quota",
								"TABLE",
								null,
								1L,
								List.of(new ColumnCatalog("mailUsedSize", "bigint", 20, false, "메일함 사용량 byte", false)),
								List.of(),
								List.of())))),
				List.of());
	}
}
