package org.parkjw.agent.backoffice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mariadb://127.0.0.1:3306/test",
		"spring.datasource.username=test",
		"spring.datasource.password=test",
		"spring.ai.ollama.base-url=http://127.0.0.1:11434",
		"spring.ai.ollama.chat.model=test-model",
		"ai-query.security.api-key-enabled=false",
		"ai-query.security.request-signing-enabled=true",
		"ai-query.security.request-signing-secret=test-signing-secret",
		"ai-query.security.allowed-ips[0]=127.0.0.1",
		"ai-query.catalog.storage-path=data/test-catalog-snapshot.json",
		"ai-query.audit.storage-path=data/test-audit-events.jsonl",
		"ai-query.scan.exclude-databases[0]=information_schema",
		"ai-query.policy.default-limit=100",
		"ai-query.policy.max-limit=1000",
		"ai-query.policy.query-timeout-seconds=10",
		"ai-query.policy.export-max-rows=5000",
		"ai-query.data-policy.user-name-encrypted=true"
})
class BackofficeAgentApplicationTests {

	@Test
	void contextLoads() {
	}

}
