package org.parkjw.agent.backoffice.query;

import org.springframework.stereotype.Component;

@Component
public class NoResultGuidanceService {

	private static final String MESSAGE = "조건에 맞는 조회 결과가 없습니다.";
	private static final String GUIDANCE = "실제 데이터가 없거나, 질의 조건이 너무 좁게 해석되었을 수 있습니다. 기간, 사용자, 상태 조건을 확인하거나 표현을 바꿔 다시 요청해 주세요.";

	public NoResultGuidance guidance() {
		return new NoResultGuidance(MESSAGE, GUIDANCE);
	}

	public String guidanceSentence() {
		var noResult = guidance();
		return noResult.message() + " " + noResult.guidance();
	}

	public record NoResultGuidance(String message, String guidance) {
	}
}
