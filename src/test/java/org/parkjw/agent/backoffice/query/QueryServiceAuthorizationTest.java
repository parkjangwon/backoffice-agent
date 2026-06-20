package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.parkjw.agent.backoffice.audit.AuditService;
import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.CatalogRepository;
import org.parkjw.agent.backoffice.catalog.CatalogSearchIndex;
import org.parkjw.agent.backoffice.catalog.DatabaseCatalog;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.catalog.ScopeMapping;
import org.parkjw.agent.backoffice.config.AiQueryProperties;
import org.parkjw.agent.backoffice.security.AccessRole;
import org.parkjw.agent.backoffice.security.AccessScopeResolver;

import org.junit.jupiter.api.Test;

class QueryServiceAuthorizationTest {

	@Test
	void preview_whenScopedActorRequestsData_usesExplicitScopeValues() {
		var sqlGenerator = mock(SqlGenerator.class);
		when(sqlGenerator.generate(any(QueryContext.class)))
				.thenReturn(new GeneratedSql("select email, 'example.org' as domain from `tenant_example`.`users`"));
		var executor = mock(ReadOnlyQueryExecutor.class);
		when(executor.execute(anyString(), anyInt())).thenAnswer(invocation -> new QueryResult(
				invocation.getArgument(0),
				List.of("email", "domain"),
				List.of(Map.of("email", "user@example.org", "domain", "example.org")),
				1));
		var service = service(sqlGenerator, executor);

		service.preview(new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				"example.org 사용자 수",
				null,
				10), "127.0.0.1");

		verify(sqlGenerator).generate(org.mockito.ArgumentMatchers.argThat(context ->
				context.accessContext().scopeValues().equals(List.of("example.org"))));
	}

	@Test
	void preview_whenScopedActorHasNoScopeValues_failsClosed() {
		var service = service(mock(SqlGenerator.class), mock(ReadOnlyQueryExecutor.class));

		assertThatThrownBy(() -> service.preview(new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				List.of(),
				"example.org 사용자 수",
				null,
				10), "127.0.0.1"))
				.isInstanceOf(SqlPolicyException.class)
				.hasMessageContaining("at least one scope value");
	}

	private QueryService service(SqlGenerator sqlGenerator, ReadOnlyQueryExecutor executor) {
		var properties = properties();
		var catalogRepository = mock(CatalogRepository.class);
		when(catalogRepository.current()).thenReturn(catalog());
		when(catalogRepository.currentIndex()).thenReturn(CatalogSearchIndex.empty());
		var analyzer = new SqlAnalyzer();
		return new QueryService(
				catalogRepository,
				new QueryRequestLogContext(),
				new PromptInjectionGuard(),
				sqlGenerator,
				new SqlSafetyValidator(analyzer),
				new SqlCatalogGroundingValidator(new CatalogScopeResolver(), analyzer),
				mock(SqlAccessGuard.class),
				new ScopeEnforcer(analyzer),
				executor,
				new QueryResultCache(properties),
				properties,
				mock(AuditService.class),
				new ResultPresentationService(new HumanReadableValueFormatter()),
				new AccessScopeResolver(),
				new BusinessIntentParser(new PromptDateRangeExtractor()),
				new SqlRiskGuard(new SqlAnalyzer()));
	}

	private AiQueryProperties properties() {
		return new AiQueryProperties(
				null,
				null,
				null,
				null,
				null,
				new AiQueryProperties.Cache(false, 60, 20),
				null);
	}

	private CatalogSnapshot catalog() {
		return new CatalogSnapshot(
				Instant.parse("2026-06-19T00:00:00Z"),
				"test",
				"MySQL",
				"8",
				List.of(new DatabaseCatalog("tenant_example", "tenant", "abc", List.of(
						new TableCatalog("tenant_example", "users", "TABLE", null, 10L, List.of(), List.of(), List.of())))),
				List.of(new ScopeMapping("example.org", "tenant_example", "master_catalog", "tenant_example")));
	}
}
