package org.parkjw.agent.backoffice.query;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class BusinessIntentParser {

	private static final Pattern EMAIL_ADDRESS = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", Pattern.CASE_INSENSITIVE);
	private static final Pattern DOMAIN_NAME = Pattern.compile("\\b[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z]{2,})+\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern KOREAN_YEAR_MONTH = Pattern.compile("(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월");
	private static final Pattern DASH_YEAR_MONTH = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})");
	private static final Pattern RESOLVED_START_YEAR_MONTH = Pattern.compile(">= '(20\\d{2})-(\\d{2})-");

	private final PromptDateRangeExtractor dateRangeExtractor;

	public BusinessIntentParser(PromptDateRangeExtractor dateRangeExtractor) {
		this.dateRangeExtractor = dateRangeExtractor;
	}

	public BusinessIntent parse(String prompt) {
		var rawPrompt = prompt == null ? "" : prompt;
		var normalizedPrompt = normalized(rawPrompt);
		var emails = emails(rawPrompt);
		var domains = domains(rawPrompt, emails);
		var safeForBusinessQuery = safeForBusinessQuery(normalizedPrompt);
		var category = safeForBusinessQuery ? category(normalizedPrompt) : BusinessIntentCategory.UNKNOWN;
		var metricHints = metricHints(normalizedPrompt, category);
		var orderHints = orderHints(normalizedPrompt);
		var dateRangeDescription = dateRangeExtractor.describe(rawPrompt).orElse("Resolved date range: none.");
		var requestedYearMonths = requestedYearMonths(rawPrompt, dateRangeDescription);
		var userScoped = !emails.isEmpty() || containsAny(normalizedPrompt, "사용자", "계정", "user", "account", "member");
		var catalogTokens = catalogTokens(normalizedPrompt, category, metricHints, orderHints);
		return new BusinessIntent(
				category,
				emails,
				domains,
				dateRangeDescription,
				metricHints,
				orderHints,
				userScoped,
				safeForBusinessQuery,
				noResultGuidanceSeed(category, safeForBusinessQuery),
				catalogTokens,
				requestedYearMonths);
	}

	private Set<String> emails(String prompt) {
		var emails = new LinkedHashSet<String>();
		var matcher = EMAIL_ADDRESS.matcher(prompt);
		while (matcher.find()) {
			emails.add(matcher.group().toLowerCase(Locale.ROOT));
		}
		return emails;
	}

	private Set<String> domains(String prompt, Set<String> emails) {
		var domains = new LinkedHashSet<String>();
		for (var email : emails) {
			var at = email.indexOf('@');
			if (at >= 0 && at + 1 < email.length()) {
				domains.add(email.substring(at + 1));
			}
		}
		var matcher = DOMAIN_NAME.matcher(prompt);
		while (matcher.find()) {
			var domain = matcher.group().toLowerCase(Locale.ROOT);
			if (!domain.contains("@")) {
				domains.add(domain);
			}
		}
		return domains;
	}

	private BusinessIntentCategory category(String prompt) {
		if (containsAny(prompt, "메일함", "mailbox")
				&& containsAny(prompt, "용량", "사용량", "quota", "capacity", "storage", "usage", "size")) {
			return BusinessIntentCategory.MAILBOX_USAGE;
		}
		if (containsAny(prompt, "로그인", "login", "signin", "sign in", "access history")) {
			return BusinessIntentCategory.LOGIN_HISTORY;
		}
		if (containsAny(prompt, "상태", "잠금", "정지", "휴면", "활성", "비활성", "status", "locked", "disabled", "active", "dormant")
				&& containsAny(prompt, "사용자", "계정", "user", "account", "member")) {
			return BusinessIntentCategory.USER_STATUS;
		}
		if (containsAny(prompt, "관리자", "권한", "admin", "privilege", "role")
				&& containsAny(prompt, "사용자", "계정", "user", "account", "member")) {
			return BusinessIntentCategory.USER_STATUS;
		}
		if (containsAny(prompt, "통계", "집계", "건수", "합계", "평균", "추이", "순위", "statistics", "stat", "count", "total", "sum", "average", "trend", "rank")) {
			return BusinessIntentCategory.STATISTICS;
		}
		if (containsAny(prompt, "메일", "mail", "email", "message", "송수신", "수신", "송신", "발송", "제목", "subject")) {
			return BusinessIntentCategory.MAIL_SEARCH;
		}
		if (containsAny(prompt, "서비스", "이용", "사용", "이벤트", "로그", "service", "usage", "event", "log")) {
			return BusinessIntentCategory.SERVICE_USAGE;
		}
		return BusinessIntentCategory.UNKNOWN;
	}

	private Set<String> metricHints(String prompt, BusinessIntentCategory category) {
		var hints = new LinkedHashSet<String>();
		if (containsAny(prompt, "용량", "사용량", "quota", "capacity", "storage", "usage", "size")) {
			hints.addAll(Set.of("usage", "quota", "size"));
		}
		if (containsAny(prompt, "건수", "횟수", "count", "total")) {
			hints.addAll(Set.of("count", "total"));
		}
		if (containsAny(prompt, "평균", "average", "avg")) {
			hints.add("average");
		}
		if (category == BusinessIntentCategory.LOGIN_HISTORY) {
			hints.add("login");
		}
		if (category == BusinessIntentCategory.SERVICE_USAGE) {
			hints.add("usage");
		}
		return hints;
	}

	private Set<String> orderHints(String prompt) {
		var hints = new LinkedHashSet<String>();
		if (containsAny(prompt, "상위", "최대", "많은", "top", "highest", "largest", "most", "desc", "descending")) {
			hints.add("top");
			hints.add("desc");
		}
		if (containsAny(prompt, "하위", "최소", "적은", "bottom", "lowest", "smallest", "least", "asc", "ascending")) {
			hints.add("bottom");
			hints.add("asc");
		}
		if (containsAny(prompt, "최근", "latest", "recent", "newest")) {
			hints.add("latest");
			hints.add("desc");
		}
		if (containsAny(prompt, "오래된", "earliest", "oldest")) {
			hints.add("earliest");
			hints.add("asc");
		}
		return hints;
	}

	private Set<String> requestedYearMonths(String prompt, String dateRangeDescription) {
		var yearMonths = new LinkedHashSet<String>();
		addYearMonths(prompt, KOREAN_YEAR_MONTH, yearMonths);
		addYearMonths(prompt, DASH_YEAR_MONTH, yearMonths);
		addYearMonths(dateRangeDescription, RESOLVED_START_YEAR_MONTH, yearMonths);
		return yearMonths;
	}

	private void addYearMonths(String value, Pattern pattern, Set<String> yearMonths) {
		var matcher = pattern.matcher(value);
		while (matcher.find()) {
			var month = Integer.parseInt(matcher.group(2));
			if (month >= 1 && month <= 12) {
				yearMonths.add(matcher.group(1) + "%02d".formatted(month));
			}
		}
	}

	private Set<String> catalogTokens(
			String prompt,
			BusinessIntentCategory category,
			Set<String> metricHints,
			Set<String> orderHints) {
		var tokens = new LinkedHashSet<String>();
		for (var token : prompt.split("[^a-z0-9]+")) {
			if (!token.isBlank() && token.length() > 2) {
				tokens.add(token);
			}
		}
		tokens.addAll(metricHints);
		tokens.addAll(orderHints);
		addSemanticTokens(prompt, category, tokens);
		return tokens;
	}

	private void addSemanticTokens(String prompt, BusinessIntentCategory category, Set<String> tokens) {
		if (category == BusinessIntentCategory.MAIL_SEARCH || containsAny(prompt, "메일", "mail")) {
			tokens.addAll(Set.of("mail", "email", "message", "smtp", "imap"));
		}
		if (category == BusinessIntentCategory.MAILBOX_USAGE || containsAny(prompt, "메일함", "mailbox", "box")) {
			tokens.addAll(Set.of("mailbox", "box", "folder", "imap", "mail"));
		}
		if (containsAny(prompt, "용량", "사용량", "capacity", "quota", "size", "usage")) {
			tokens.addAll(Set.of("quota", "size", "used", "usage", "capacity", "volume", "storage"));
		}
		if (containsAny(prompt, "계정", "사용자", "account", "user")) {
			tokens.addAll(Set.of("account", "user", "member", "email"));
		}
		if (containsAny(prompt, "관리자", "권한", "admin", "privilege", "role")) {
			tokens.addAll(Set.of("admin", "role", "privilege", "permission", "auth"));
		}
		if (category == BusinessIntentCategory.LOGIN_HISTORY || containsAny(prompt, "로그인", "login")) {
			tokens.addAll(Set.of("login", "history", "log", "access"));
		}
		if (containsAny(prompt, "로그", "log", "history")) {
			tokens.addAll(Set.of("log", "history", "event"));
		}
		if (containsAny(prompt, "송수신", "수신", "송신", "발송", "send", "receive", "sent")) {
			tokens.addAll(Set.of("sender", "recipient", "from", "address", "sent", "received", "rcpt"));
		}
		if (category == BusinessIntentCategory.STATISTICS) {
			tokens.addAll(Set.of("stat", "stats", "count", "total", "summary"));
		}
	}

	private String noResultGuidanceSeed(BusinessIntentCategory category, boolean safeForBusinessQuery) {
		if (!safeForBusinessQuery) {
			return "보안 정책상 시스템/권한/비밀 정보 요청은 업무 조회로 처리하지 않습니다.";
		}
		return switch (category) {
			case MAIL_SEARCH -> "조건에 맞는 메일 송수신 내역이 없을 수 있습니다. 이메일, 도메인, 기간 조건을 확인하세요.";
			case MAILBOX_USAGE -> "메일함 용량 사용량 결과가 없을 수 있습니다. 사용자/도메인 또는 기간 조건을 확인하세요.";
			case LOGIN_HISTORY -> "로그인 이력 결과가 없을 수 있습니다. 계정, 도메인, 기간 조건을 확인하세요.";
			case USER_STATUS -> "사용자 상태 결과가 없을 수 있습니다. 계정 상태 조건과 도메인 범위를 확인하세요.";
			case STATISTICS -> "통계 결과가 없을 수 있습니다. 집계 기준, 기간, 도메인 범위를 확인하세요.";
			case SERVICE_USAGE -> "서비스 이용 결과가 없을 수 있습니다. 이벤트 종류, 사용자, 기간 조건을 확인하세요.";
			case UNKNOWN -> "업무 의도를 확정하지 못했습니다. 사용자, 도메인, 기간, 조회 대상을 더 구체적으로 입력하세요.";
		};
	}

	private boolean safeForBusinessQuery(String prompt) {
		if (prompt.isBlank()) {
			return true;
		}
		return !containsAny(
				prompt,
				"ignore previous", "ignore all", "system prompt", "developer prompt", "hidden prompt",
				"prompt injection", "jailbreak", "bypass", "override", "schema", "informationschema",
				"password", "passwd", "credential", "secret", "token", "api key",
				"권한 우회", "시스템 프롬프트", "개발자 프롬프트", "프롬프트 무시", "스키마",
				"비밀번호", "암호", "토큰", "시크릿",
				"insert ", "update ", "delete ", "drop ", "truncate ", "alter ", "grant ", "revoke ");
	}

	private boolean containsAny(String value, String... tokens) {
		for (var token : tokens) {
			if (value.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private String normalized(String value) {
		return value.toLowerCase(Locale.ROOT).replace("_", "");
	}
}
