package org.parkjw.agent.backoffice.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;

import org.parkjw.agent.backoffice.query.NoResultGuidanceService;
import org.parkjw.agent.backoffice.query.QueryResult;
import org.parkjw.agent.backoffice.query.QueryResultViewMapper;

import org.junit.jupiter.api.Test;

class CsvExporterTest {

	private final NoResultGuidanceService noResultGuidanceService = new NoResultGuidanceService();
	private final CsvExporter exporter = new CsvExporter(
			new QueryResultViewMapper(noResultGuidanceService),
			noResultGuidanceService);

	@Test
	void export_whenCellStartsWithFormulaPrefix() {
		// given
		var row = new LinkedHashMap<String, Object>();
		row.put("email", "=cmd|' /C calc'!A0");
		var result = new QueryResult("select email from users", List.of("email"), List.of(row), 1);

		// when
		var csv = exporter.export(result);

		// then
		assertThat(csv).contains("'=cmd|' /C calc'!A0");
	}

	@Test
	void export_whenFormattedValueExists_usesDisplayLabelAndFormattedValue() {
		var row = new LinkedHashMap<String, Object>();
		row.put("mailUsedSize", 1_258_072_482L);
		row.put("mailUsedSizeText", "1.17 GB");
		var result = new QueryResult(
				"select mailUsedSize from quota",
				List.of("mailUsedSize", "mailUsedSizeText"),
				List.of(row),
				1,
				java.util.Map.of("mailUsedSize", "메일함 사용량"));

		var csv = exporter.export(result);

		assertThat(csv).contains("메일함 사용량");
		assertThat(csv).contains("1.17 GB");
		assertThat(csv).doesNotContain("mailUsedSize");
		assertThat(csv).doesNotContain("1258072482");
	}

	@Test
	void export_whenQueryResultHasNoRows_returnsGuidanceCsvWithoutInternals() {
		var result = new QueryResult(
				"select domain, tenant_id from tenant_example.mail_hidden_scope",
				List.of("domain", "tenant_id"),
				List.of(),
				0);

		var csv = exporter.export(result);

		assertThat(csv).startsWith("안내");
		assertThat(csv)
				.contains("조건에 맞는 조회 결과가 없습니다.")
				.contains("실제 데이터가 없거나")
				.contains("조건이 너무 좁게 해석되었을 수 있습니다")
				.contains("표현을 바꿔 다시 요청해 주세요");
		assertThat(csv)
				.doesNotContain("select")
				.doesNotContain("tenant_example")
				.doesNotContain("mail_hidden_scope")
				.doesNotContain("tenant_id")
				.doesNotContain("domain");
		assertThat(csv.lines()).hasSize(2);
	}
}
