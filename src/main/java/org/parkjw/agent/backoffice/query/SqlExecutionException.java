package org.parkjw.agent.backoffice.query;

import java.sql.SQLException;
import java.util.Set;

public class SqlExecutionException extends SqlPolicyException {

	private static final Set<Integer> RETRYABLE_VENDOR_CODES = Set.of(
			1052,
			1054,
			1064,
			1146);

	public SqlExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

	public boolean retryableSqlGenerationFailure() {
		var exception = sqlException();
		if (exception == null) {
			return false;
		}
		if (RETRYABLE_VENDOR_CODES.contains(exception.getErrorCode())) {
			return true;
		}
		var state = exception.getSQLState();
		return "42S02".equals(state) || "42S22".equals(state);
	}

	private SQLException sqlException() {
		var cause = getCause();
		while (cause != null) {
			if (cause instanceof SQLException sqlException) {
				return sqlException;
			}
			cause = cause.getCause();
		}
		return null;
	}
}
