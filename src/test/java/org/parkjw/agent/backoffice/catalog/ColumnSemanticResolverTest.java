package org.parkjw.agent.backoffice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColumnSemanticResolverTest {

	@Test
	void semanticType_whenColumnLooksLikeMailboxSize_detectsByteSize() {
		// when
		var semanticType = ColumnSemanticResolver.semanticType("mailUsedSize", "bigint", "메일함 사용량 byte");
		var unit = ColumnSemanticResolver.unit("mailUsedSize", "bigint", "메일함 사용량 byte", semanticType);

		// then
		assertThat(semanticType).isEqualTo(ColumnSemanticType.BYTE_SIZE);
		assertThat(unit).isEqualTo(ColumnUnit.BYTES);
	}

	@Test
	void semanticType_whenColumnLooksLikeEmail_detectsEmail() {
		assertThat(ColumnSemanticResolver.semanticType("emailId", "varchar", "이메일 주소"))
				.isEqualTo(ColumnSemanticType.EMAIL);
	}

	@Test
	void columnCatalog_whenLegacyConstructorIsUsed_populatesLabelAndSemanticConfidence() {
		// when
		var column = new ColumnCatalog("delivery_status", "varchar", 32, true, "Delivery status", false);

		// then
		assertThat(column.semanticType()).isEqualTo(ColumnSemanticType.STATUS);
		assertThat(column.unit()).isEqualTo(ColumnUnit.NONE);
		assertThat(column.displayName()).isEqualTo("Delivery status");
		assertThat(column.label()).isEqualTo("Delivery status");
		assertThat(column.semanticConfidence()).isGreaterThanOrEqualTo(0.7);
	}
}
