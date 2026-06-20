package org.parkjw.agent.backoffice.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.parkjw.agent.backoffice.audit.AuditService;
import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.catalog.CatalogRepository;
import org.parkjw.agent.backoffice.catalog.DatabaseCatalog;
import org.parkjw.agent.backoffice.catalog.TableCatalog;
import org.parkjw.agent.backoffice.catalog.ScopeMapping;
import org.parkjw.agent.backoffice.config.AiQueryProperties;
import org.parkjw.agent.backoffice.security.AccessContext;
import org.parkjw.agent.backoffice.security.AccessRole;
import org.parkjw.agent.backoffice.security.AccessScopeResolver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(OutputCaptureExtension.class)
class QueryServiceCacheTest {

	@Test
	void preview_whenSameRequestIsRepeated_usesCachedResult(CapturedOutput output) {
		// given
		var properties = new AiQueryProperties(
				null,
				null,
				null,
				null,
				null,
				null,
				new AiQueryProperties.Cache(true, 60, 20));
		var catalogRepository = mock(CatalogRepository.class);
		when(catalogRepository.current()).thenReturn(catalog());
		when(catalogRepository.currentIndex()).thenReturn(org.parkjw.agent.backoffice.catalog.CatalogSearchIndex.empty());
		var sqlGenerator = mock(SqlGenerator.class);
		when(sqlGenerator.generate(any(QueryContext.class)))
				.thenReturn(new GeneratedSql("select email, 'example.org' as domain from `tenant_example`.`users`"));
		var executor = mock(ReadOnlyQueryExecutor.class);
		when(executor.execute(anyString(), anyInt())).thenAnswer(invocation -> new QueryResult(
				invocation.getArgument(0),
				List.of("email", "domain"),
				List.of(Map.of("email", "user@example.org", "domain", "example.org")),
				1));
		var auditService = mock(AuditService.class);
		doNothing().when(auditService).record(any(QueryExecutionMetadata.class), any());
		var analyzer = new SqlAnalyzer();
		var service = new QueryService(
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
				auditService,
				new ResultPresentationService(new HumanReadableValueFormatter()),
				new AccessScopeResolver(),
				new BusinessIntentParser(new PromptDateRangeExtractor()),
				new SqlRiskGuard(new SqlAnalyzer()));
		var request = new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				"user@example.org 사용자 수",
				null,
				10);

		// when
		var first = service.preview(request, "127.0.0.1");
		var second = service.preview(request, "127.0.0.1");

		// then
		assertThat(second).isEqualTo(first);
		verify(sqlGenerator, times(1)).generate(any(QueryContext.class));
		verify(executor, times(1)).execute(anyString(), anyInt());
		verify(auditService, times(2)).record(any(QueryExecutionMetadata.class), any());
		assertThat(output).doesNotContain("select email");
		assertThat(output).doesNotContain("user@example.org");
	}

	@Test
	void preview_whenQuerySucceeds_recordsStageLatencyMetadata() {
		// given
		var properties = new AiQueryProperties(
				null,
				null,
				null,
				null,
				null,
				null,
				new AiQueryProperties.Cache(false, 60, 20));
		var catalogRepository = mock(CatalogRepository.class);
		when(catalogRepository.current()).thenReturn(catalog());
		when(catalogRepository.currentIndex()).thenReturn(org.parkjw.agent.backoffice.catalog.CatalogSearchIndex.empty());
		var sqlGenerator = mock(SqlGenerator.class);
		when(sqlGenerator.generate(any(QueryContext.class)))
				.thenReturn(new GeneratedSql("select email, 'example.org' as domain from `tenant_example`.`users`"));
		var executor = mock(ReadOnlyQueryExecutor.class);
		when(executor.execute(anyString(), anyInt())).thenAnswer(invocation -> new QueryResult(
				invocation.getArgument(0),
				List.of("email", "domain"),
				List.of(Map.of("email", "user@example.org", "domain", "example.org")),
				1));
		var auditService = mock(AuditService.class);
		doNothing().when(auditService).record(any(QueryExecutionMetadata.class), any());
		var service = service(properties, catalogRepository, sqlGenerator, executor, auditService);
		var request = new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				"user@example.org 사용자 수",
				null,
				10);

		// when
		service.preview(request, "127.0.0.1");

		// then
		var metadata = ArgumentCaptor.forClass(QueryExecutionMetadata.class);
		verify(auditService).record(metadata.capture(), any());
		assertThat(metadata.getValue().stageLatencyMs())
				.containsKeys("sqlGenerationMs", "scopeEnforcementMs", "sqlExecutionMs", "presentationMs");
		assertThat(metadata.getValue().stageLatencyMs().values())
				.allSatisfy(value -> assertThat(value).isNotNegative());
	}

	@Test
	void preview_whenExecutionFails_retriesSqlGenerationOnce() {
		// given
		var properties = new AiQueryProperties(
				null,
				null,
				null,
				null,
				null,
				null,
				new AiQueryProperties.Cache(false, 60, 20));
		var catalogRepository = mock(CatalogRepository.class);
		when(catalogRepository.current()).thenReturn(catalog());
		when(catalogRepository.currentIndex()).thenReturn(org.parkjw.agent.backoffice.catalog.CatalogSearchIndex.empty());
		var sqlGenerator = mock(SqlGenerator.class);
		when(sqlGenerator.generate(any(QueryContext.class)))
				.thenReturn(new GeneratedSql("select broken, 'example.org' as domain from `tenant_example`.`users`"));
		when(sqlGenerator.generate(any(QueryContext.class), anyString()))
				.thenReturn(new GeneratedSql("select email, 'example.org' as domain from `tenant_example`.`users`"));
		var executor = mock(ReadOnlyQueryExecutor.class);
		when(executor.execute(anyString(), anyInt()))
				.thenThrow(new SqlExecutionException(
						"Read-only query execution failed.",
						new SQLException("unknown column", "42S22", 1054)))
				.thenAnswer(invocation -> new QueryResult(
						invocation.getArgument(0),
						List.of("email", "domain"),
						List.of(Map.of("email", "user@example.org", "domain", "example.org")),
						1));
		var auditService = mock(AuditService.class);
		doNothing().when(auditService).record(any(QueryExecutionMetadata.class), any());
		var service = service(properties, catalogRepository, sqlGenerator, executor, auditService);
		var request = new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				"user@example.org 사용자 수",
				null,
				10);

		// when
		var result = service.preview(request, "127.0.0.1");

		// then
		assertThat(result.rowCount()).isEqualTo(1);
		verify(sqlGenerator).generate(any(QueryContext.class));
		verify(sqlGenerator).generate(any(QueryContext.class), anyString());
		verify(executor, times(2)).execute(anyString(), anyInt());
	}

	@Test
	void preview_whenExecutionFailureIsNotSqlGenerationFailure_doesNotRetry() {
		// given
		var properties = new AiQueryProperties(
				null,
				null,
				null,
				null,
				null,
				null,
				new AiQueryProperties.Cache(false, 60, 20));
		var catalogRepository = mock(CatalogRepository.class);
		when(catalogRepository.current()).thenReturn(catalog());
		when(catalogRepository.currentIndex()).thenReturn(org.parkjw.agent.backoffice.catalog.CatalogSearchIndex.empty());
		var sqlGenerator = mock(SqlGenerator.class);
		when(sqlGenerator.generate(any(QueryContext.class)))
				.thenReturn(new GeneratedSql("select email, 'example.org' as domain from `tenant_example`.`users`"));
		var executor = mock(ReadOnlyQueryExecutor.class);
		when(executor.execute(anyString(), anyInt()))
				.thenThrow(new SqlExecutionException(
						"Read-only query execution failed.",
						new SQLException("connection failed", "08001", 0)));
		var auditService = mock(AuditService.class);
		var service = service(properties, catalogRepository, sqlGenerator, executor, auditService);
		var request = new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				"user@example.org 사용자 수",
				null,
				10);

		// when / then
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.preview(request, "127.0.0.1"))
				.isInstanceOf(SqlExecutionException.class);
		verify(sqlGenerator).generate(any(QueryContext.class));
		verify(sqlGenerator, times(0)).generate(any(QueryContext.class), anyString());
		verify(executor).execute(anyString(), anyInt());
	}

	@Test
	void cache_whenSameKeyIsRequestedConcurrently_computesOnlyOnce() throws Exception {
		// given
		var properties = new AiQueryProperties(
				null,
				null,
				null,
				null,
				null,
				null,
				new AiQueryProperties.Cache(true, 60, 20));
		var cache = new QueryResultCache(properties);
		var request = new QueryRequest(
				"operator-123",
				AccessRole.SCOPED,
				List.of("example.org"),
				"user@example.org 사용자 수",
				null,
				10);
		var accessContext = new AccessContext(
				request.actorId(),
				request.role(),
				List.of("example.org"),
				List.of(),
				true);
		var key = QueryResultCache.key("PREVIEW", request, accessContext, catalog(), properties, 10);
		var started = new CountDownLatch(1);
		var supplierCalls = new AtomicInteger();
		var result = new QueryResult(
				"select count(*) from `tenant_example`.`users`",
				List.of("count"),
				List.of(Map.of("count", 1)),
				1);
		var executorService = Executors.newFixedThreadPool(4);
		try {
			var futures = java.util.stream.IntStream.range(0, 4)
					.mapToObj(ignored -> executorService.submit(() -> {
						started.await(1, TimeUnit.SECONDS);
						return cache.getOrCompute(key, () -> {
							supplierCalls.incrementAndGet();
							try {
								Thread.sleep(100);
							}
							catch (InterruptedException exception) {
								Thread.currentThread().interrupt();
								throw new IllegalStateException(exception);
							}
							return result;
						});
					}))
					.toList();

			// when
			started.countDown();
			var lookups = futures.stream()
					.map(future -> {
						try {
							return future.get(2, TimeUnit.SECONDS);
						}
						catch (Exception exception) {
							throw new IllegalStateException(exception);
						}
					})
					.toList();

			// then
			assertThat(lookups).allSatisfy(lookup -> assertThat(lookup.result()).isEqualTo(result));
			assertThat(supplierCalls.get()).isEqualTo(1);
			assertThat(lookups.stream().filter(QueryResultCache.Lookup::cacheHit).count()).isEqualTo(3);
		}
		finally {
			executorService.shutdownNow();
			assertThat(executorService.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
		}
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

	private QueryService service(
			AiQueryProperties properties,
			CatalogRepository catalogRepository,
			SqlGenerator sqlGenerator,
			ReadOnlyQueryExecutor executor,
			AuditService auditService) {
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
				auditService,
				new ResultPresentationService(new HumanReadableValueFormatter()),
				new AccessScopeResolver(),
				new BusinessIntentParser(new PromptDateRangeExtractor()),
				new SqlRiskGuard(new SqlAnalyzer()));
	}
}
