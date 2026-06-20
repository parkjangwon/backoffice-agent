package org.parkjw.agent.backoffice.catalog;

import java.util.Locale;

public final class ColumnSemanticResolver {

	private ColumnSemanticResolver() {
	}

	public static ColumnSemanticType semanticType(String name, String typeName, String remarks) {
		var text = normalized(name, typeName, remarks);
		if (containsAny(text, "email", "mailaddress", "e-mail", "메일주소", "이메일")) {
			return ColumnSemanticType.EMAIL;
		}
		if (containsAny(text, "domain", "도메인")) {
			return ColumnSemanticType.DOMAIN;
		}
		if (containsAny(text, "percent", "percentage", "ratio", "rate", "률", "율", "퍼센트", "%")) {
			return ColumnSemanticType.PERCENT;
		}
		if (containsAny(text, "byte", "bytes", "size", "quota", "capacity", "volume", "용량", "크기", "할당량")) {
			return ColumnSemanticType.BYTE_SIZE;
		}
		if (containsAny(text, "datetime", "timestamp", "date", "time", "created", "updated", "sent", "received", "login", "일시", "날짜", "시간")) {
			return ColumnSemanticType.DATETIME;
		}
		if (containsAny(text, "millisecond", "milliseconds", "elapsedms", "durationms")) {
			return ColumnSemanticType.DURATION;
		}
		if (containsAny(text, "second", "seconds", "duration", "elapsed", "기간", "소요")) {
			return ColumnSemanticType.DURATION;
		}
		if (containsAny(text, "count", "cnt", "totalrow", "row", "건수", "횟수", "개수", "총수")) {
			return ColumnSemanticType.COUNT;
		}
		if (containsAny(text, "status", "state", "flag", "type", "상태", "구분")) {
			return ColumnSemanticType.STATUS;
		}
		if (containsAny(text, "boolean", "bool", "enabled", "disabled", "active", "deleted", "yn", "여부")) {
			return ColumnSemanticType.BOOLEAN;
		}
		return ColumnSemanticType.UNKNOWN;
	}

	public static ColumnUnit unit(String name, String typeName, String remarks, ColumnSemanticType semanticType) {
		var text = normalized(name, typeName, remarks);
		if (containsAny(text, "millisecond", "milliseconds", "_ms", "ms ", "elapsedms", "durationms")) {
			return ColumnUnit.MILLISECONDS;
		}
		if (containsAny(text, "second", "seconds", "_sec", "sec ", "초")) {
			return ColumnUnit.SECONDS;
		}
		if (containsAny(text, "kilobyte", "kilobytes", "kbyte", "kbytes", " kb", "_kb")) {
			return ColumnUnit.KILOBYTES;
		}
		if (containsAny(text, "megabyte", "megabytes", "mbyte", "mbytes", " mb", "_mb")) {
			return ColumnUnit.MEGABYTES;
		}
		if (semanticType == ColumnSemanticType.BYTE_SIZE) {
			return ColumnUnit.BYTES;
		}
		if (semanticType == ColumnSemanticType.PERCENT) {
			return ColumnUnit.PERCENT;
		}
		if (semanticType == ColumnSemanticType.COUNT) {
			return ColumnUnit.COUNT;
		}
		return ColumnUnit.NONE;
	}

	public static String displayName(String name, String remarks) {
		if (remarks != null && !remarks.isBlank()) {
			return remarks.strip();
		}
		return name;
	}

	public static String label(String name, String remarks) {
		if (remarks != null && !remarks.isBlank()) {
			return remarks.strip();
		}
		if (name == null || name.isBlank()) {
			return "Unknown";
		}
		var spaced = name.replace("_", " ")
				.replaceAll("([a-z])([A-Z])", "$1 $2")
				.strip();
		return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
	}

	public static double semanticConfidence(String name, String typeName, String remarks, ColumnSemanticType semanticType) {
		if (semanticType == ColumnSemanticType.UNKNOWN) {
			return 0.2;
		}
		var confidence = 0.6;
		var nameOnly = normalized(name, "", "");
		var remarksOnly = normalized("", "", remarks);
		if (!nameOnly.isBlank() && semanticType(name, "", "") == semanticType) {
			confidence += 0.2;
		}
		if (!remarksOnly.isBlank() && semanticType("", "", remarks) == semanticType) {
			confidence += 0.15;
		}
		if (typeName != null && !typeName.isBlank()) {
			confidence += 0.05;
		}
		return Math.min(0.95, confidence);
	}

	private static String normalized(String name, String typeName, String remarks) {
		return ("%s %s %s".formatted(
				name == null ? "" : name,
				typeName == null ? "" : typeName,
				remarks == null ? "" : remarks))
				.toLowerCase(Locale.ROOT)
				.replace("-", "")
				.replace("_", "");
	}

	private static boolean containsAny(String text, String... keywords) {
		for (var keyword : keywords) {
			if (text.contains(keyword.toLowerCase(Locale.ROOT).replace("-", "").replace("_", ""))) {
				return true;
			}
		}
		return false;
	}
}
