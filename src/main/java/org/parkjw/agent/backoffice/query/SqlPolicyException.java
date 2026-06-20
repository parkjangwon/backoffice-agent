package org.parkjw.agent.backoffice.query;

public class SqlPolicyException extends RuntimeException {

	public SqlPolicyException(String message) {
		super(message);
	}

	public SqlPolicyException(String message, Throwable cause) {
		super(message, cause);
	}
}
