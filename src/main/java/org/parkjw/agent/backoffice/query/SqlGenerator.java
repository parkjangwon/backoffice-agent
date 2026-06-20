package org.parkjw.agent.backoffice.query;

public interface SqlGenerator {

	GeneratedSql generate(QueryContext context);

	default GeneratedSql generate(QueryContext context, String rejectedExecutionReason) {
		return generate(context);
	}
}
