package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoResultGuidanceServiceTest {

	private final NoResultGuidanceService service = new NoResultGuidanceService();

	@Test
	void guidance_whenQueryResultHasNoRows_returnsSafeUserFacingCopy() {
		var guidance = service.guidance();

		assertThat(guidance.message()).isEqualTo("조건에 맞는 조회 결과가 없습니다.");
		assertThat(guidance.guidance())
				.contains("실제 데이터가 없거나")
				.contains("조건이 너무 좁게 해석되었을 수 있습니다")
				.contains("표현을 바꿔 다시 요청해 주세요");
		assertThat(guidance.toString())
				.doesNotContain("select")
				.doesNotContain("tenant_example")
				.doesNotContain("mail_hidden_scope")
				.doesNotContain("tenant_id")
				.doesNotContain("domain");
	}

}
