package org.parkjw.agent.backoffice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.parkjw.agent.backoffice.config.AiQueryProperties;

import org.junit.jupiter.api.Test;

class ScanPatternMatcherTest {

	@Test
	void shouldScan_whenIncludeDatabasesIsEmptyExceptBuiltInAndUserExcludes() {
		// given
		var properties = new AiQueryProperties(
					null,
					null,
					null,
					new AiQueryProperties.Scan(List.of(), List.of("tmp_%")),
					null,
					null,
					null);
		var matcher = new ScanPatternMatcher(properties);

		// when
		var mailTenant = matcher.shouldScan("mail_tenant_001");
		var systemDatabase = matcher.shouldScan("information_schema");
		var mysqlCompatibilityDatabase = matcher.shouldScan("#mysql50##innodb_redo");
		var temporaryDatabase = matcher.shouldScan("tmp_work");

		// then
		assertThat(mailTenant).isTrue();
		assertThat(systemDatabase).isFalse();
		assertThat(mysqlCompatibilityDatabase).isFalse();
		assertThat(temporaryDatabase).isFalse();
	}

	@Test
	void shouldScan_whenIncludeDatabasesHasPatternAndExcludeStillWins() {
		// given
		var properties = new AiQueryProperties(
					null,
					null,
					null,
					new AiQueryProperties.Scan(List.of("mail_%"), List.of("mail_tmp_%")),
					null,
					null,
					null);
		var matcher = new ScanPatternMatcher(properties);

		// when
		var mailTenant = matcher.shouldScan("mail_tenant_001");
		var archiveTenant = matcher.shouldScan("archive_tenant_001");
		var excludedMail = matcher.shouldScan("mail_tmp_001");

		// then
		assertThat(mailTenant).isTrue();
		assertThat(archiveTenant).isFalse();
		assertThat(excludedMail).isFalse();
	}

	@Test
	void shouldScan_whenRequestTargetsSystemDatabase_keepsBuiltInExcludes() {
		// given
		var properties = new AiQueryProperties(
					null,
					null,
					null,
					new AiQueryProperties.Scan(List.of(), List.of()),
					null,
					null,
					null);
		var matcher = new ScanPatternMatcher(properties);
		var request = new CatalogScanRequest(
				List.of("information_schema", "tenant_alpha"),
				List.of("%"),
				List.of("tenant_tmp_%"));

		// when
		var systemDatabase = matcher.shouldScan("information_schema", request);
		var tenantDatabase = matcher.shouldScan("tenant_alpha", request);
		var untargetedDatabase = matcher.shouldScan("tenant_beta", request);
		var excludedTenant = matcher.shouldScan("tenant_tmp_001", request);

		// then
		assertThat(systemDatabase).isFalse();
		assertThat(tenantDatabase).isTrue();
		assertThat(untargetedDatabase).isFalse();
		assertThat(excludedTenant).isFalse();
	}
}
