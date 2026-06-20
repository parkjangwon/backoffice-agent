package org.parkjw.agent.backoffice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AgentConfigurationValidatorTest {

	@Test
	void validate_whenRequiredConfigurationExists() {
		var validator = new AgentConfigurationValidator(validEnvironment(), properties(List.of("local-key")));

		assertThatCode(validator::validate).doesNotThrowAnyException();
	}

	@Test
	void validate_whenApiKeyPlaceholderStringIsUnresolved() {
		var validator = new AgentConfigurationValidator(validEnvironment(), properties(List.of("${api.key}")));

		assertThatThrownBy(validator::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ai-query.security.api-keys");
	}

	@Test
	void validate_whenOllamaBaseUrlIsMissing() {
		var environment = validEnvironment()
				.withProperty("spring.ai.ollama.base-url", "");
		var validator = new AgentConfigurationValidator(environment, properties(List.of("local-key")));

		assertThatThrownBy(validator::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("spring.ai.ollama.base-url");
	}

	@Test
	void validate_whenRequestSigningIsEnabledAndSecretIsMissing() {
		var environment = validEnvironment()
				.withProperty("ai-query.security.request-signing-enabled", "true");
		var validator = new AgentConfigurationValidator(
				environment,
				new AiQueryProperties(
						new AiQueryProperties.Security(true, List.of("local-key"), List.of("127.0.0.1"), true, "", 300),
						null,
						null,
						null,
						null,
						null,
						null));

		assertThatThrownBy(validator::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ai-query.security.request-signing-secret");
	}

	@Test
	void validate_whenApiKeyIsDisabledAndRequestSigningSecretIsMissing() {
		var environment = validEnvironment()
				.withProperty("ai-query.security.api-key-enabled", "false")
				.withProperty("ai-query.security.request-signing-enabled", "true");
		var validator = new AgentConfigurationValidator(
				environment,
				new AiQueryProperties(
						new AiQueryProperties.Security(false, List.of(), List.of("127.0.0.1"), true, "", 300),
						null,
						null,
						null,
						null,
						null,
						null));

		assertThatThrownBy(validator::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ai-query.security.request-signing-secret");
	}

	@Test
	void validate_whenApiKeyAndRequestSigningAreBothDisabled() {
		var environment = validEnvironment()
				.withProperty("ai-query.security.api-key-enabled", "false")
				.withProperty("ai-query.security.request-signing-enabled", "false");
		var validator = new AgentConfigurationValidator(
				environment,
				new AiQueryProperties(
						new AiQueryProperties.Security(false, List.of(), List.of("127.0.0.1"), false, "", 300),
						null,
						null,
						null,
						null,
						null,
						null));

		assertThatThrownBy(validator::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ai-query.security.request-signing-enabled");
	}

	@Test
	void validate_whenApiKeyIsDisabledAndRequestSigningSecretExists() {
		var environment = validEnvironment()
				.withProperty("ai-query.security.api-key-enabled", "false")
				.withProperty("ai-query.security.request-signing-enabled", "true")
				.withProperty("ai-query.security.request-signing-secret", "local-secret");
		var validator = new AgentConfigurationValidator(
				environment,
				new AiQueryProperties(
						new AiQueryProperties.Security(false, List.of(), List.of("127.0.0.1"), true, "local-secret", 300),
						null,
						null,
						null,
						null,
						null,
						null));

		assertThatCode(validator::validate).doesNotThrowAnyException();
	}

	private MockEnvironment validEnvironment() {
		return new MockEnvironment()
				.withProperty("spring.datasource.url", "jdbc:mariadb://127.0.0.1:3306/test")
				.withProperty("spring.datasource.username", "test")
				.withProperty("spring.datasource.password", "test")
				.withProperty("spring.ai.ollama.base-url", "http://127.0.0.1:11434")
				.withProperty("spring.ai.ollama.chat.model", "test-model")
				.withProperty("ai-query.security.api-key-enabled", "true")
				.withProperty("ai-query.security.allowed-ips[0]", "127.0.0.1")
				.withProperty("ai-query.catalog.storage-path", "data/catalog-snapshot.json")
				.withProperty("ai-query.audit.storage-path", "data/audit-events.jsonl")
				.withProperty("ai-query.scan.exclude-databases[0]", "information_schema")
				.withProperty("ai-query.policy.default-limit", "100")
				.withProperty("ai-query.policy.max-limit", "1000")
				.withProperty("ai-query.policy.query-timeout-seconds", "10")
				.withProperty("ai-query.policy.export-max-rows", "5000")
				.withProperty("ai-query.data-policy.user-name-encrypted", "true");
	}

	private AiQueryProperties properties(List<String> apiKeys) {
		return new AiQueryProperties(
				new AiQueryProperties.Security(true, apiKeys, List.of("127.0.0.1"), false, "", 300),
				null,
				null,
				null,
				null,
				null,
				null);
	}
}
