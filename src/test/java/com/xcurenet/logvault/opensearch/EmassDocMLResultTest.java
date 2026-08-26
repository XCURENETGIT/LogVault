package com.xcurenet.logvault.opensearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmassDoc.MLResult.merge() - ML 결과 병합 로직")
class EmassDocMLResultTest {

	private EmassDoc.MLResult base() {
		EmassDoc.MLResult r = new EmassDoc.MLResult();
		r.setCodeExist(false);
		r.setCategory(1);
		r.setProbs(0.5f);
		r.setResult(0);
		r.setKeywords(new ArrayList<>(List.of("keyword1")));
		return r;
	}

	@Nested
	@DisplayName("null 병합")
	class NullMerge {
		@Test
		@DisplayName("other가 null이면 변경 없음")
		void mergeNull_noChange() {
			EmassDoc.MLResult r = base();
			r.merge(null);
			assertEquals(1, r.getCategory());
			assertEquals(0.5f, r.getProbs());
		}
	}

	@Nested
	@DisplayName("필드별 병합 규칙")
	class FieldMerge {
		@Test
		@DisplayName("category: 더 큰 값으로 갱신")
		void category_takesMax() {
			EmassDoc.MLResult r = base();
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setCategory(2);
			r.merge(other);
			assertEquals(2, r.getCategory());
		}

		@Test
		@DisplayName("category: 작은 값은 무시")
		void category_ignoresSmaller() {
			EmassDoc.MLResult r = base();
			r.setCategory(2);
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setCategory(1);
			r.merge(other);
			assertEquals(2, r.getCategory());
		}

		@Test
		@DisplayName("probs: 더 큰 값으로 갱신")
		void probs_takesMax() {
			EmassDoc.MLResult r = base();
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setProbs(0.9f);
			r.merge(other);
			assertEquals(0.9f, r.getProbs(), 0.001f);
		}

		@Test
		@DisplayName("codeExist: OR 연산 (한쪽이 true면 true)")
		void codeExist_orLogic() {
			EmassDoc.MLResult r = base();
			assertFalse(r.isCodeExist());
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setCodeExist(true);
			r.merge(other);
			assertTrue(r.isCodeExist());
		}

		@Test
		@DisplayName("keywords: 합산 (addAll)")
		void keywords_merged() {
			EmassDoc.MLResult r = base();
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setKeywords(List.of("keyword2", "keyword3"));
			r.merge(other);
			assertEquals(3, r.getKeywords().size());
			assertTrue(r.getKeywords().contains("keyword2"));
		}

		@Test
		@DisplayName("keywords: base가 null이면 other 키워드로 초기화")
		void keywords_initFromOther() {
			EmassDoc.MLResult r = base();
			r.setKeywords(null);
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setKeywords(List.of("new"));
			r.merge(other);
			assertNotNull(r.getKeywords());
			assertEquals(1, r.getKeywords().size());
		}

		@Test
		@DisplayName("result: 양수 중 더 큰 값으로 갱신")
		void result_takesPositiveMax() {
			EmassDoc.MLResult r = base();
			r.setResult(2);
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setResult(5);
			r.merge(other);
			assertEquals(5, r.getResult());
		}

		@Test
		@DisplayName("similarityExist: OR 연산")
		void similarity_orLogic() {
			EmassDoc.MLResult r = base();
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setSimilarityExist(true);
			other.setSimilarityId("sim-1");
			other.setSimilarityName("test");
			other.setSimilarityScore(0.95f);
			r.merge(other);
			assertTrue(r.isSimilarityExist());
			assertEquals("sim-1", r.getSimilarityId());
			assertEquals(0.95f, r.getSimilarityScore(), 0.001f);
		}

		@Test
		@DisplayName("similarity: ID, 이름, 점수는 최고 점수 결과를 한 세트로 유지")
		void similarity_keepsHighestScoreResultAsSet() {
			EmassDoc.MLResult r = base();
			r.setSimilarityExist(true);
			r.setSimilarityId("high-id");
			r.setSimilarityName("high-name");
			r.setSimilarityScore(95f);

			EmassDoc.MLResult lower = new EmassDoc.MLResult();
			lower.setSimilarityExist(true);
			lower.setSimilarityId("low-id");
			lower.setSimilarityName("low-name");
			lower.setSimilarityScore(70f);
			r.merge(lower);

			assertEquals("high-id", r.getSimilarityId());
			assertEquals("high-name", r.getSimilarityName());
			assertEquals(95f, r.getSimilarityScore(), 0.001f);

			EmassDoc.MLResult higher = new EmassDoc.MLResult();
			higher.setSimilarityExist(true);
			higher.setSimilarityId("highest-id");
			higher.setSimilarityName("highest-name");
			higher.setSimilarityScore(100f);
			r.merge(higher);

			assertEquals("highest-id", r.getSimilarityId());
			assertEquals("highest-name", r.getSimilarityName());
			assertEquals(100f, r.getSimilarityScore(), 0.001f);
		}

		@Test
		@DisplayName("message: non-blank 값으로 갱신")
		void message_takesNonBlank() {
			EmassDoc.MLResult r = base();
			r.setMessage(null);
			EmassDoc.MLResult other = new EmassDoc.MLResult();
			other.setMessage("detected");
			other.setResult(1);
			r.merge(other);
			assertEquals("detected", r.getMessage());
		}
	}
}
