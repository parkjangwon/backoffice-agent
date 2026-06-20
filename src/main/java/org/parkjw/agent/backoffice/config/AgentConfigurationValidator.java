package org.parkjw.agent.backoffice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentConfigurationValidator implements ApplicationRunner {

	private final Environment environment;
	private final AiQueryProperties properties;

	public AgentConfigurationValidator(Environment environment, AiQueryProperties properties) {
		this.environment = environment;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		validate();
	}

	void validate() {
		requireProperty("spring.datasource.url");
		requireProperty("spring.datasource.username");
		requireProperty("spring.datasource.password");
		requireProperty("spring.ai.ollama.base-url");
		requireProperty("spring.ai.ollama.chat.model");
		requireProperty("ai-query.catalog.storage-path");
		requireProperty("ai-query.audit.storage-path");
		requireProperty("ai-query.scan.exclude-databases[0]");
		requirePositiveInt("ai-query.policy.default-limit");
		requirePositiveInt("ai-query.policy.max-limit");
		requirePositiveInt("ai-query.policy.query-timeout-seconds");
		requirePositiveInt("ai-query.policy.export-max-rows");
		requireProperty("ai-query.data-policy.user-name-encrypted");
		requireAtLeastOneAuthMechanism();
		requireApiKeys();
		requireRequestSigningSecret();
	}

	private void requireProperty(String propertyName) {
		var value = environment.getProperty(propertyName);
		if (missing(value)) {
			throw new IllegalStateException("Missing required configuration: " + propertyName);
		}
	}

	private void requireApiKeys() {
		if (!properties.security().apiKeyEnabled()) {
			return;
		}
		requireProperty("ai-query.security.api-key-enabled");
		requireProperty("ai-query.security.allowed-ips[0]");
		var apiKeys = properties.security().apiKeys();
		if (apiKeys.isEmpty() || apiKeys.stream().anyMatch(this::missing)) {
			throw new IllegalStateException("Missing required configuration: ai-query.security.api-keys");
		}
	}

	private void requireAtLeastOneAuthMechanism() {
		if (!properties.security().apiKeyEnabled() && !properties.security().requestSigningEnabled()) {
			throw new IllegalStateException("Missing required configuration: ai-query.security.request-signing-enabled");
		}
	}

	private void requireRequestSigningSecret() {
		if (properties.security().requestSigningEnabled()) {
			requireProperty("ai-query.security.request-signing-secret");
		}
	}

	private void requirePositiveInt(String propertyName) {
		var value = environment.getProperty(propertyName, Integer.class);
		if (value == null || value <= 0) {
			throw new IllegalStateException("Missing required configuration: " + propertyName);
		}
	}

	private boolean missing(String value) {
		return value == null || value.isBlank() || unresolvedPlaceholder(value);
	}

	private boolean unresolvedPlaceholder(String value) {
		return value.contains("${") || value.contains("}");
	}
}
