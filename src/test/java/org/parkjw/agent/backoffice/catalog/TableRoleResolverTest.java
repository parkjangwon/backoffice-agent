package org.parkjw.agent.backoffice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TableRoleResolverTest {

	@Test
	void tableCatalog_whenEventSignalsArePresent_infersRoleConfidenceAndUsableIndexes() {
		// given
		var columns = List.of(
				new ColumnCatalog("tenant_id", "bigint", 19, false, "Tenant id", true),
				new ColumnCatalog("actor_email", "varchar", 255, true, "Actor email", false),
				new ColumnCatalog("event_status", "varchar", 32, true, "Status", false),
				new ColumnCatalog("created_at", "timestamp", 0, false, "Created at", false),
				new ColumnCatalog("payload_size_bytes", "bigint", 19, true, "Payload size", false),
				new ColumnCatalog("retry_count", "int", 10, true, "Retry count", false));

		// when
		var table = new TableCatalog(
				"tenant_alpha",
				"audit_event",
				"BASE TABLE",
				"Tenant audit events",
				5000L,
				columns,
				List.of("id"),
				List.of("idx_tenant_created"),
				null,
				null,
				null,
				false,
				List.of("tenant_id", "created_at"));

		// then
		assertThat(table.role()).isEqualTo(TableRole.EVENT);
		assertThat(table.roleConfidence()).isGreaterThanOrEqualTo(0.75);
		assertThat(table.rowEstimateSource()).isEqualTo("snapshot_default");
		assertThat(table.indexed()).isTrue();
		assertThat(table.usableIndexColumns()).containsExactly("tenant_id", "created_at");
	}
}
