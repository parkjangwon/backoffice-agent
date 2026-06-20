package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class PromptDateRangeExtractorTest {

	@Test
	void describe_whenPromptHasExplicitKoreanYearMonth() {
		// given
		var extractor = new PromptDateRangeExtractor(fixedClock());

		// when
		var description = extractor.describe("2026년 5월 이용 로그");

		// then
		assertThat(description).hasValueSatisfying(value -> assertThat(value)
				.contains("2026-05-01 00:00:00")
				.contains("2026-06-01 00:00:00"));
	}

	@Test
	void describe_whenPromptHasKoreanMonthOnly_usesCurrentYear() {
		// given
		var extractor = new PromptDateRangeExtractor(fixedClock());

		// when
		var description = extractor.describe("5월 이용 로그");

		// then
		assertThat(description).hasValueSatisfying(value -> assertThat(value)
				.contains("2026-05-01 00:00:00")
				.contains("2026-06-01 00:00:00"));
	}

	private Clock fixedClock() {
		return Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));
	}
}
