package org.parkjw.agent.backoffice.query;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.parkjw.agent.backoffice.catalog.ColumnSemanticType;
import org.parkjw.agent.backoffice.catalog.ColumnUnit;

import org.springframework.stereotype.Component;

@Component
public class HumanReadableValueFormatter {

	private static final BigDecimal KIB = BigDecimal.valueOf(1024);
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public String format(Object value, ColumnSemanticType semanticType, ColumnUnit unit) {
		if (value == null) {
			return null;
		}
		if (unit == ColumnUnit.BYTES || unit == ColumnUnit.KILOBYTES || unit == ColumnUnit.MEGABYTES) {
			return formatByteSize(value, unit);
		}
		if (unit == ColumnUnit.PERCENT || semanticType == ColumnSemanticType.PERCENT) {
			return formatPercent(value);
		}
		if (unit == ColumnUnit.COUNT || semanticType == ColumnSemanticType.COUNT) {
			return formatCount(value);
		}
		if (unit == ColumnUnit.MILLISECONDS || unit == ColumnUnit.SECONDS || semanticType == ColumnSemanticType.DURATION) {
			return formatDuration(value, unit);
		}
		if (semanticType == ColumnSemanticType.DATETIME) {
			return formatDateTime(value);
		}
		if (semanticType == ColumnSemanticType.BOOLEAN) {
			return formatBoolean(value);
		}
		return null;
	}

	private String formatByteSize(Object value, ColumnUnit unit) {
		return number(value)
				.map(number -> {
					var bytes = switch (unit) {
						case KILOBYTES -> number.multiply(KIB);
						case MEGABYTES -> number.multiply(KIB).multiply(KIB);
						default -> number;
					};
					return formatBytes(bytes);
				})
				.orElse(null);
	}

	private String formatBytes(BigDecimal bytes) {
		var absolute = bytes.abs();
		if (absolute.compareTo(KIB) < 0) {
			return bytes.setScale(0, RoundingMode.HALF_UP).toPlainString() + " B";
		}
		var units = new String[] { "KB", "MB", "GB", "TB", "PB" };
		var value = bytes;
		var index = -1;
		while (value.abs().compareTo(KIB) >= 0 && index < units.length - 1) {
			value = value.divide(KIB, 4, RoundingMode.HALF_UP);
			index++;
		}
		return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + units[index];
	}

	private String formatPercent(Object value) {
		return number(value)
				.map(number -> number.setScale(number.scale() > 0 ? 2 : 0, RoundingMode.HALF_UP)
						.stripTrailingZeros()
						.toPlainString() + "%")
				.orElse(null);
	}

	private String formatCount(Object value) {
		return number(value)
				.map(number -> new DecimalFormat("#,##0.##").format(number))
				.orElse(null);
	}

	private String formatDuration(Object value, ColumnUnit unit) {
		return number(value)
				.map(number -> {
					var seconds = unit == ColumnUnit.MILLISECONDS
							? number.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
							: number;
					if (seconds.compareTo(BigDecimal.valueOf(60)) < 0) {
						return seconds.stripTrailingZeros().toPlainString() + "초";
					}
					var minutes = seconds.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
					if (minutes.compareTo(BigDecimal.valueOf(60)) < 0) {
						return minutes.stripTrailingZeros().toPlainString() + "분";
					}
					var hours = minutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
					return hours.stripTrailingZeros().toPlainString() + "시간";
				})
				.orElse(null);
	}

	private String formatDateTime(Object value) {
		if (value instanceof Timestamp timestamp) {
			return DATE_TIME_FORMATTER.format(timestamp.toLocalDateTime());
		}
		if (value instanceof LocalDateTime localDateTime) {
			return DATE_TIME_FORMATTER.format(localDateTime);
		}
		if (value instanceof Date date) {
			return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
		}
		return number(value)
				.map(this::epochDateTime)
				.orElse(null);
	}

	private String epochDateTime(BigDecimal value) {
		var raw = value.longValue();
		var instant = raw >= 1_000_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
		return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
	}

	private String formatBoolean(Object value) {
		if (value instanceof Boolean bool) {
			return bool ? "예" : "아니오";
		}
		if (value instanceof Number number) {
			return number.longValue() == 0 ? "아니오" : "예";
		}
		if (value instanceof String text) {
			return switch (text.strip().toLowerCase()) {
				case "y", "yes", "true", "1" -> "예";
				case "n", "no", "false", "0" -> "아니오";
				default -> null;
			};
		}
		return null;
	}

	private java.util.Optional<BigDecimal> number(Object value) {
		return switch (value) {
			case BigDecimal decimal -> java.util.Optional.of(decimal);
			case Number number -> java.util.Optional.of(BigDecimal.valueOf(number.doubleValue()));
			case String text when !text.isBlank() -> {
				try {
					yield java.util.Optional.of(new BigDecimal(text.strip()));
				}
				catch (NumberFormatException exception) {
					yield java.util.Optional.empty();
				}
			}
			default -> java.util.Optional.empty();
		};
	}
}
