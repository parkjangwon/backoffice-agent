package org.parkjw.agent.backoffice.query;

import java.util.List;
import java.util.Locale;

import org.parkjw.agent.backoffice.security.AccessContext;

import org.springframework.stereotype.Component;

@Component
public class PromptInjectionGuard {

	private static final List<String> MUTATION_INTENTS = List.of(
			"삭제해", "삭제 해", "지워", "수정해", "수정 해", "변경해", "변경 해", "삽입해", "생성해", "초기화해", "권한 부여",
			"drop", "delete", "update", "insert",
			"alter", "create", "truncate", "merge", "replace", "grant", "revoke", "flush", "kill", "set global");

	private static final List<String> JAILBREAK_INTENTS = List.of(
			"무시", "우회", "ignore previous", "ignore all", "disregard", "bypass", "override", "jailbreak",
			"act as", "developer mode", "do anything now", "dan");

	private static final List<String> SCOPE_BYPASS_INTENTS = List.of(
			"권한 없이", "전체 스코프", "다른 스코프", "다른 scope",
			"all tenants", "other tenant", "other domain", "other scope", "different scope");

	private static final List<String> SYSTEM_DATA_INTENTS = List.of(
			"시스템 구조", "시스템 테이블", "시스템 데이터", "메타데이터", "계정 비밀번호", "시스템 프롬프트", "개발자 메시지",
			"테이블 구조", "컬럼 구조", "스키마 구조", "데이터베이스 구조", "테이블 목록", "컬럼 목록",
			"권한 테이블", "설정 테이블", "환경변수",
			"api key", "api-key", "apikey", "password", "schema", "information_schema", "mysql.user",
			"performance_schema", "sys.", "oauth2_authorization", "token", "secret", "credential", "environment variable",
			"env var");

	private static final List<String> RAW_SQL_INTENTS = List.of(
			"union select", "sleep(", "benchmark(", "load_file(", "outfile", "dumpfile", "@@version", "current_user()",
			"database()");

	public void inspect(QueryRequest request, AccessContext accessContext) {
		var prompt = request.prompt().toLowerCase(Locale.ROOT);
		rejectAny(prompt, JAILBREAK_INTENTS, "Prompt appears to request policy bypass or jailbreak behavior.");
		rejectAny(prompt, MUTATION_INTENTS, "Prompt appears to request data mutation.");
		rejectAny(prompt, RAW_SQL_INTENTS, "Prompt contains SQL injection indicators.");
		rejectSystemData(prompt);
		if (!accessContext.globalAccess()) {
			rejectAny(prompt, SCOPE_BYPASS_INTENTS, "Prompt appears to request scope bypass.");
			rejectOtherExternalScopeValue(prompt, accessContext);
		}
	}

	private void rejectSystemData(String prompt) {
		for (var keyword : SYSTEM_DATA_INTENTS) {
			if (prompt.contains(keyword)) {
				throw new SqlPolicyException("Prompt appears to request system or secret data.");
			}
		}
	}

	private void rejectOtherExternalScopeValue(String prompt, AccessContext accessContext) {
		var domainPattern = "[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}";
		var matcher = java.util.regex.Pattern.compile(domainPattern).matcher(prompt);
		while (matcher.find()) {
			var scopeValue = matcher.group();
			if (accessContext.scopeValues().stream().noneMatch(allowed -> allowed.equalsIgnoreCase(scopeValue))) {
				throw new SqlPolicyException("Prompt requests a value outside the access scope: " + scopeValue);
			}
		}
	}

	private void rejectAny(String prompt, List<String> keywords, String message) {
		for (var keyword : keywords) {
			if (prompt.contains(keyword)) {
				throw new SqlPolicyException(message);
			}
		}
	}
}
