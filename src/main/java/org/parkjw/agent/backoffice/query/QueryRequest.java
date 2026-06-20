package org.parkjw.agent.backoffice.query;

import java.util.List;

import org.parkjw.agent.backoffice.security.AccessRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QueryRequest(
		@NotBlank String actorId,
		@NotNull AccessRole role,
		List<String> scopeValues,
		@NotBlank String prompt,
		ResponseFormat responseFormat,
		Integer limit) {

	public QueryRequest(String actorId, AccessRole role, String prompt, ResponseFormat responseFormat, Integer limit) {
		this(actorId, role, List.of(), prompt, responseFormat, limit);
	}

	public QueryRequest {
		scopeValues = scopeValues == null ? List.of() : scopeValues.stream()
				.filter(value -> value != null && !value.isBlank())
				.map(String::strip)
				.toList();
	}
}
