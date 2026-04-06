package com.xcurenet.logvault.opensearch;

import com.xcurenet.logvault.exception.IndexerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndexService - 색인 로직")
class IndexServiceTest {

	@Mock
	private OpenSearchRestTemplate template;
	@InjectMocks
	private IndexService indexService;

	@Nested
	@DisplayName("indexData() - 데이터 색인")
	class IndexData {

		@Test
		@DisplayName("정상 데이터 → template.save() 호출")
		void validData_shouldSave() {
			EmassDoc doc = new EmassDoc();
			doc.setMsgid("test-id");
			when(template.save(any(), any(IndexCoordinates.class))).thenReturn(Collections.singleton(doc));

			assertDoesNotThrow(() -> indexService.indexData(doc, "emass-20251104"));
			verify(template).save(eq(doc), any(IndexCoordinates.class));
		}

		@Test
		@DisplayName("indexName이 null → IndexerException")
		void nullIndex_shouldThrow() {
			EmassDoc doc = new EmassDoc();
			assertThrows(IndexerException.class, () -> indexService.indexData(doc, null));
		}

		@Test
		@DisplayName("data가 null → IndexerException")
		void nullData_shouldThrow() {
			assertThrows(IndexerException.class, () -> indexService.indexData(null, "emass-20251104"));
		}

		@Test
		@DisplayName("template.save() 실패 → IndexerException 래핑")
		void saveFailure_shouldThrowWrapped() {
			EmassDoc doc = new EmassDoc();
			when(template.save(any(), any(IndexCoordinates.class))).thenThrow(new RuntimeException("Connection refused"));

			assertThrows(IndexerException.class, () -> indexService.indexData(doc, "emass-20251104"));
		}
	}

	@Nested
	@DisplayName("deleteIndices() - 인덱스 삭제 가드")
	class DeleteIndices {
		@Test
		@DisplayName("null 인덱스명 → IndexerException")
		void nullIndex_shouldThrow() {
			assertThrows(IndexerException.class, () -> indexService.deleteIndices(null));
		}

		@Test
		@DisplayName("빈 문자열 → IndexerException")
		void emptyIndex_shouldThrow() {
			assertThrows(IndexerException.class, () -> indexService.deleteIndices(""));
		}

		@Test
		@DisplayName("와일드카드 '*' → IndexerException (안전 가드)")
		void wildcard_shouldThrow() {
			assertThrows(IndexerException.class, () -> indexService.deleteIndices("*"));
		}

		@Test
		@DisplayName("'_all' → IndexerException (안전 가드)")
		void all_shouldThrow() {
			assertThrows(IndexerException.class, () -> indexService.deleteIndices("_all"));
		}

		@Test
		@DisplayName("시스템 인덱스(. 접두사) → IndexerException")
		void systemIndex_shouldThrow() {
			assertThrows(IndexerException.class, () -> indexService.deleteIndices(".kibana"));
		}
	}
}
