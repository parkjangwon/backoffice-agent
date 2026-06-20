package org.parkjw.agent.backoffice.common;

import org.parkjw.agent.backoffice.query.QueryRequestLogContext;
import org.parkjw.agent.backoffice.query.SqlPolicyException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

	private final QueryRequestLogContext logContext;

	public ApiExceptionHandler(QueryRequestLogContext logContext) {
		this.logContext = logContext;
	}

	@ExceptionHandler(SqlPolicyException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> sqlPolicy(SqlPolicyException exception) {
			logContext.current().ifPresentOrElse(
					entry -> log.warn(
							"ai-query blocked actorId={} role={} clientIp={} promptHash={} reason={}",
							entry.request().actorId(),
							entry.request().role(),
							entry.clientIp(),
							LogFingerprints.sha256(entry.request().prompt()),
							exception.getMessage()),
				() -> log.warn("ai-query blocked reason={}", exception.getMessage()));
		return ApiResponse.error(exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> validation(MethodArgumentNotValidException exception) {
		return ApiResponse.error("Request validation failed.");
	}
}
