package org.parkjw.agent.backoffice.query;

import org.parkjw.agent.backoffice.audit.AuditService;
import org.parkjw.agent.backoffice.catalog.CatalogRepository;
import org.parkjw.agent.backoffice.catalog.CatalogSnapshot;
import org.parkjw.agent.backoffice.common.LogFingerprints;
import org.parkjw.agent.backoffice.config.AiQueryProperties;
import org.parkjw.agent.backoffice.security.AccessContext;
import org.parkjw.agent.backoffice.security.AccessScopeResolver;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueryService {

	private final CatalogRepository catalogRepository;
	private final QueryRequestLogContext logContext;
	private final PromptInjectionGuard promptGuard;
	private final SqlGenerator sqlGenerator;
	private final SqlSafetyValidator validator;
	private final SqlCatalogGroundingValidator groundingValidator;
	private final SqlAccessGuard accessGuard;
	private final ScopeEnforcer scopeEnforcer;
	private final ReadOnlyQueryExecutor executor;
	private final QueryResultCache queryResultCache;
	private final AiQueryProperties properties;
	private final AuditService auditService;
	private final ResultPresentationService presentationService;
	private final AccessScopeResolver accessScopeResolver;
	private final BusinessIntentParser intentParser;
	private final SqlRiskGuard riskGuard;

	public QueryService(
			CatalogRepository catalogRepository,
			QueryRequestLogContext logContext,
			PromptInjectionGuard promptGuard,
			SqlGenerator sqlGenerator,
			SqlSafetyValidator validator,
			SqlCatalogGroundingValidator groundingValidator,
			SqlAccessGuard accessGuard,
			ScopeEnforcer scopeEnforcer,
			ReadOnlyQueryExecutor executor,
			QueryResultCache queryResultCache,
			AiQueryProperties properties,
			AuditService auditService,
			ResultPresentationService presentationService,
			AccessScopeResolver accessScopeResolver,
			BusinessIntentParser intentParser,
			SqlRiskGuard riskGuard) {
		this.catalogRepository = catalogRepository;
		this.logContext = logContext;
		this.promptGuard = promptGuard;
		this.sqlGenerator = sqlGenerator;
		this.validator = validator;
		this.groundingValidator = groundingValidator;
		this.accessGuard = accessGuard;
		this.scopeEnforcer = scopeEnforcer;
		this.executor = executor;
		this.queryResultCache = queryResultCache;
		this.properties = properties;
		this.auditService = auditService;
		this.presentationService = presentationService;
		this.accessScopeResolver = accessScopeResolver;
		this.intentParser = intentParser;
		this.riskGuard = riskGuard;
	}

	public QueryResult preview(QueryRequest request, String clientIp) {
		return runWithLogging("PREVIEW", request, properties.policy().clampLimit(request.limit()), clientIp);
	}

	public QueryResult export(QueryRequest request, String clientIp) {
		return runWithLogging("EXPORT", request, properties.policy().exportMaxRows(), clientIp);
	}

	private QueryResult runWithLogging(String action, QueryRequest request, int limit, String clientIp) {
		var requestId = UUID.randomUUID().toString();
		var startedNanos = System.nanoTime();
		logContext.set(request, clientIp);
		log.info(
				"ai-query request requestId={} action={} actorId={} role={} clientIp={} limit={} promptHash={}",
				requestId,
				action,
				request.actorId(),
				request.role(),
				clientIp,
				limit,
				LogFingerprints.sha256(request.prompt()));
		try {
			var execution = execute(action, request, limit);
			var latencyMs = elapsedMillis(startedNanos);
			var result = execution.result();
			auditService.record(
					metadata(requestId, action, "SUCCESS", latencyMs, execution.cacheHit(), execution.context(), result, execution.stageLatencyMs()),
					request);
			log.info(
					"ai-query success requestId={} action={} actorId={} effectiveRole={} clientIp={} rowCount={} latencyMs={} cacheHit={} catalogVersion={} stageLatencyMs={} outcome=SUCCESS",
					requestId,
					action,
					request.actorId(),
					execution.context().accessContext().role(),
					clientIp,
					result.rowCount(),
					latencyMs,
					execution.cacheHit(),
					execution.context().catalog().metadata().catalogVersion(),
					execution.stageLatencyMs());
			log.debug("ai-query sql requestId={} sqlHash={}", requestId, sqlHash(result.sql()));
			return result;
		}
		catch (RuntimeException exception) {
			log.warn(
					"ai-query blocked action={} actorId={} role={} clientIp={} promptHash={} reason={}",
					action,
					request.actorId(),
					request.role(),
					clientIp,
					LogFingerprints.sha256(request.prompt()),
					exception.getMessage());
			throw exception;
		}
		finally {
			logContext.clear();
		}
	}

	private QueryExecution execute(String action, QueryRequest request, int limit) {
		var stages = new StageLatencyRecorder();
		var effectiveScope = stages.measure("accessScopeMs", () -> accessScopeResolver.resolve(request));
		if ("EXPORT".equals(action) && !effectiveScope.exportAllowed()) {
			throw new SqlPolicyException("Effective access scope is not allowed to export results.");
		}
		var accessContext = effectiveScope.toAccessContext();
		stages.measure("promptGuardMs", () -> promptGuard.inspect(request, accessContext));
		var catalog = stages.measure("catalogLoadMs", catalogRepository::current);
		var catalogIndex = stages.measure("catalogIndexLoadMs", catalogRepository::currentIndex);
		var intent = stages.measure("intentParseMs", () -> intentParser.parse(request.prompt()));
		if (!intent.safeForBusinessQuery()) {
			throw new SqlPolicyException(intent.noResultGuidanceSeed());
		}
		var context = new QueryContext(action, request, effectiveScope, accessContext, intent, catalog, catalogIndex, limit);
		var cacheKey = QueryResultCache.key(action, request, accessContext, catalog, properties, limit);
		var cached = stages.measure("cacheLookupMs", () -> queryResultCache.get(cacheKey));
		if (cached.isPresent()) {
			log.info(
					"ai-query generated actorId={} role={} source=cache",
					request.actorId(),
					accessContext.role());
			return new QueryExecution(cached.get(), context, true, stages.snapshot());
		}
		var lookup = queryResultCache.computeIfAbsent(
				cacheKey,
				() -> generateAndRunWithExecutionRetry(request, limit, accessContext, intent, catalog, catalogIndex, action, context, stages));
		return new QueryExecution(lookup.result(), context, lookup.cacheHit(), stages.snapshot());
	}

	private QueryResult generateAndRunWithExecutionRetry(
			QueryRequest request,
			int limit,
			AccessContext accessContext,
			BusinessIntent intent,
			CatalogSnapshot catalog,
			org.parkjw.agent.backoffice.catalog.CatalogSearchIndex catalogIndex,
			String action,
			QueryContext context,
			StageLatencyRecorder stages) {
		try {
			return generateAndRun(request, limit, accessContext, intent, catalog, catalogIndex, action, context, stages, null);
		}
		catch (SqlExecutionException exception) {
			if (!exception.retryableSqlGenerationFailure()) {
				throw exception;
			}
			log.warn(
					"ai-query execution retry actorId={} role={} reason={}",
					request.actorId(),
					accessContext.role(),
					exception.getMessage());
			return generateAndRun(
					request,
					limit,
					accessContext,
					intent,
					catalog,
					catalogIndex,
					action,
					context,
					stages,
					"Previous SQL failed during read-only execution. Generate a simpler MySQL/MariaDB SELECT using only listed catalog columns, valid aliases, and conservative joins.");
		}
	}

	private QueryResult generateAndRun(
			QueryRequest request,
			int limit,
			AccessContext accessContext,
			BusinessIntent intent,
			CatalogSnapshot catalog,
			org.parkjw.agent.backoffice.catalog.CatalogSearchIndex catalogIndex,
			String action,
			QueryContext context,
			StageLatencyRecorder stages,
			String rejectedExecutionReason) {
		var generated = stages.measure("sqlGenerationMs", () -> rejectedExecutionReason == null
				? sqlGenerator.generate(context)
				: sqlGenerator.generate(context, rejectedExecutionReason));
		var selectOnlySql = stages.measure("readOnlyValidationMs", () -> validator.requireReadOnlySelect(generated.sql()));
		stages.measure("catalogGroundingMs", () -> groundingValidator.requireCatalogTables(selectOnlySql, catalog, accessContext));
		stages.measure("accessGuardMs", () -> accessGuard.inspect(selectOnlySql, accessContext, catalog));
		var scopedSql = stages.measure("scopeEnforcementMs", () -> scopeEnforcer.enforce(selectOnlySql, accessContext, catalog, catalogIndex));
		var finalSql = stages.measure("finalReadOnlyValidationMs", () -> validator.requireReadOnlySelect(scopedSql));
		stages.measure("finalCatalogGroundingMs", () -> groundingValidator.requireCatalogTables(finalSql, catalog, accessContext));
		stages.measure("finalAccessGuardMs", () -> accessGuard.inspect(finalSql, accessContext, catalog));
		stages.measure("riskGuardMs", () -> riskGuard.inspect(finalSql, accessContext, intent, catalog, action));
		log.info(
				"ai-query generated actorId={} role={} sqlHash={}",
				request.actorId(),
				accessContext.role(),
				sqlHash(finalSql));
		var raw = stages.measure("sqlExecutionMs", () -> executor.execute(finalSql, limit));
		return stages.measure("presentationMs", () -> presentationService.present(raw, catalog));
	}

	private long elapsedMillis(long startedNanos) {
		return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
	}

	private String sqlHash(String sql) {
		if (sql == null || sql.isBlank()) {
			return null;
		}
		try {
			return LogFingerprints.sha256(sql);
		}
		catch (IllegalStateException exception) {
			throw new SqlPolicyException("SHA-256 digest is not available.", exception);
		}
	}

	private record QueryExecution(QueryResult result, QueryContext context, boolean cacheHit, Map<String, Long> stageLatencyMs) {
	}

	private QueryExecutionMetadata metadata(
			String requestId,
			String action,
			String outcome,
			long latencyMs,
			boolean cacheHit,
			QueryContext context,
			QueryResult result,
			Map<String, Long> stageLatencyMs) {
		return new QueryExecutionMetadata(
				requestId,
				action,
				outcome,
				latencyMs,
				cacheHit,
				context.catalog().metadata().catalogVersion(),
				context.accessContext().role(),
				context.accessContext().scopeValues(),
				context.accessContext().allowedDatabases(),
				context.limit(),
				result.rowCount(),
				sqlHash(result.sql()),
				stageLatencyMs);
	}

	private static final class StageLatencyRecorder {

		private final Map<String, Long> latencies = new LinkedHashMap<>();

		private <T> T measure(String name, java.util.function.Supplier<T> supplier) {
			var started = System.nanoTime();
			try {
				return supplier.get();
			}
			finally {
				record(name, started);
			}
		}

		private void measure(String name, Runnable runnable) {
			var started = System.nanoTime();
			try {
				runnable.run();
			}
			finally {
				record(name, started);
			}
		}

		private void record(String name, long startedNanos) {
			latencies.merge(name, Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000), Long::sum);
		}

		private Map<String, Long> snapshot() {
			return Map.copyOf(latencies);
		}
	}
}
