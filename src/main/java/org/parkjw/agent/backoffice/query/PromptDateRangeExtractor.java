package org.parkjw.agent.backoffice.query;

import java.time.Clock;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class PromptDateRangeExtractor {

	private static final Pattern KOREAN_YEAR_MONTH = Pattern.compile("(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월");
	private static final Pattern KOREAN_MONTH = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*월");
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Clock clock;

	public PromptDateRangeExtractor() {
		this(Clock.systemDefaultZone());
	}

	PromptDateRangeExtractor(Clock clock) {
		this.clock = clock;
	}

	public Optional<String> describe(String prompt) {
		return yearMonth(prompt).map(yearMonth -> {
			var start = yearMonth.atDay(1).atStartOfDay();
			var end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
			return """
					Resolved date range: >= '%s' and < '%s'.
					If comparing epoch seconds, use UNIX_TIMESTAMP with those boundaries.
					If comparing epoch milliseconds, use UNIX_TIMESTAMP(boundary) * 1000.
					Do not use another year or month.
					""".formatted(start.format(FORMATTER), end.format(FORMATTER)).trim();
		});
	}

	private Optional<YearMonth> yearMonth(String prompt) {
		var explicit = KOREAN_YEAR_MONTH.matcher(prompt);
		if (explicit.find()) {
			return Optional.of(YearMonth.of(Integer.parseInt(explicit.group(1)), Integer.parseInt(explicit.group(2))));
		}
		var monthOnly = KOREAN_MONTH.matcher(prompt);
		if (monthOnly.find()) {
			return Optional.of(YearMonth.of(Year.now(clock).getValue(), Integer.parseInt(monthOnly.group(1))));
		}
		return Optional.empty();
	}
}
