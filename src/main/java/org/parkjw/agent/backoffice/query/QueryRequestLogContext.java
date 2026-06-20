package org.parkjw.agent.backoffice.query;

import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class QueryRequestLogContext {

	private final ThreadLocal<Entry> current = new ThreadLocal<>();

	public void set(QueryRequest request, String clientIp) {
		current.set(new Entry(request, clientIp));
	}

	public Optional<Entry> current() {
		return Optional.ofNullable(current.get());
	}

	public void clear() {
		current.remove();
	}

	public record Entry(QueryRequest request, String clientIp) {
	}
}
