package org.parkjw.agent.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class OpenSourceProjectContractTest {

	private static final Path ROOT = Path.of("").toAbsolutePath();

	@Test
	void projectMetadata_whenOpenSourceVersion_isVendorNeutralBackofficeAgent() throws Exception {
		var settings = Files.readString(ROOT.resolve("settings.gradle"));
		var build = Files.readString(ROOT.resolve("build.gradle"));
		var readme = Files.readString(ROOT.resolve("README.md"));
		var docs = Files.readString(ROOT.resolve("docs/client-integration.md"));

		assertThat(settings).contains("rootProject.name = 'backoffice-agent'");
		assertThat(build)
				.contains("group = 'org.parkjw.agent'")
				.contains("description = 'Backoffice Agent'");
		assertThat(readme).contains("# backoffice-agent");
		assertThat(readme + docs)
				.doesNotContain("Crinity")
				.doesNotContain("crinity")
				.doesNotContain("크리니티")
				.doesNotContain("govkorea")
				.doesNotContain("kisa.or.kr")
				.doesNotContain("nia.or.kr");
	}

	@Test
	void packages_whenOpenSourceVersion_useParkjwNamespace() {
		assertThatCode(() -> Class.forName("org.parkjw.agent.backoffice.BackofficeAgentApplication"))
				.doesNotThrowAnyException();
		assertThatCode(() -> Class.forName("com.crinity.agent.backoffice.CrinityBackofficeAgentApplication"))
				.isInstanceOf(ClassNotFoundException.class);
	}

	@Test
	void requestContract_whenOpenSourceVersion_usesActorAndExplicitScopes() throws Exception {
		var queryRequest = Class.forName("org.parkjw.agent.backoffice.query.QueryRequest");
		var components = Arrays.stream(queryRequest.getRecordComponents())
				.map(component -> component.getName())
				.toList();

		assertThat(components)
				.contains("actorId", "role", "scopeValues", "prompt", "responseFormat", "limit")
				.doesNotContain("adminEmail");

		var accessRole = Class.forName("org.parkjw.agent.backoffice.security.AccessRole");
		assertThat(Arrays.stream(accessRole.getEnumConstants()).map(Object::toString))
				.containsExactly("GLOBAL", "SCOPED");
	}

	@Test
	void accessModel_whenOpenSourceVersion_doesNotExposeTenantOrDomainAdminTypes() throws Exception {
		var sourceNames = javaSourceNames(ROOT.resolve("src/main/java/org/parkjw/agent/backoffice"));
		var sourceText = javaSourceText(ROOT.resolve("src/main/java/org/parkjw/agent/backoffice"));

		assertThat(sourceNames)
				.noneMatch(name -> name.contains("Tenant"))
				.noneMatch(name -> name.contains("Admin"));
		assertThat(sourceText)
				.doesNotContain("DOMAIN_ADMIN")
				.doesNotContain("SUPER_ADMIN")
				.doesNotContain("allowed domain")
				.doesNotContain("email domain -> database")
				.doesNotContain("tenant mappings")
				.doesNotContain("tenant scope");
		assertThatCode(() -> Class.forName("org.parkjw.agent.backoffice.catalog.ScopeMapping"))
				.doesNotThrowAnyException();
		assertThatCode(() -> Class.forName("org.parkjw.agent.backoffice.query.ScopeEnforcer"))
				.doesNotThrowAnyException();
	}

	@Test
	void databaseDialects_whenOpenSourceVersion_coverCommonBackofficeDatabases() throws Exception {
		var dialect = Class.forName("org.parkjw.agent.backoffice.catalog.DatabaseDialect");

		assertThat(Arrays.stream(dialect.getEnumConstants()).map(Object::toString))
				.contains("MYSQL", "MARIADB", "POSTGRESQL", "SQLITE", "ORACLE");
	}

	@Test
	void aiProviders_whenOpenSourceVersion_coverLocalCommercialAndCompatibleModels() throws Exception {
		var provider = Class.forName("org.parkjw.agent.backoffice.config.AiProvider");

		assertThat(Arrays.stream(provider.getEnumConstants()).map(Object::toString))
				.contains(
						"OLLAMA",
						"OPENAI",
						"ANTHROPIC",
						"AZURE_OPENAI",
						"GEMINI",
						"GOOGLE_VERTEX_AI",
						"AMAZON_BEDROCK",
						"MISTRAL",
						"DEEPSEEK",
						"Z_AI",
						"MINIMAX",
						"MIMO",
						"CURSOR",
						"OPENAI_COMPATIBLE");
	}

	@Test
	void runtimeDependencies_whenOpenSourceVersion_coverConfiguredDatabases() throws Exception {
		var build = Files.readString(ROOT.resolve("build.gradle"));

		assertThat(build)
				.contains("org.mariadb.jdbc:mariadb-java-client")
				.contains("org.postgresql:postgresql")
				.contains("org.xerial:sqlite-jdbc")
				.contains("com.oracle.database.jdbc:ojdbc11");
	}

	private static String javaSourceText(Path root) throws Exception {
		var builder = new StringBuilder();
		try (var paths = Files.walk(root)) {
			var files = paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.toList();
			for (var file : files) {
				builder.append(Files.readString(file)).append('\n');
			}
		}
		return builder.toString();
	}

	private static Stream<String> javaSourceNames(Path root) throws Exception {
		try (var paths = Files.walk(root)) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.map(path -> path.getFileName().toString())
					.toList()
					.stream();
		}
	}
}
