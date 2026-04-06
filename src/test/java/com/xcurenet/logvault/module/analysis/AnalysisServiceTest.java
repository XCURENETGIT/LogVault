package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.util.CheckWorkingDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisService - 분석 파이프라인 독립 실행 검증")
class AnalysisServiceTest {

	@Mock
	private AttachAnalysis attachAnalysis;
	@Mock
	private UserAgentAnalysis userAgentAnalysis;
	@Mock
	private KeywordAnalysis keywordAnalysis;
	@Mock
	private PrivacyAIAnalysis privacyAnalysis;
	@Mock
	private CheckWorkingDay checkWorkingDay;
	@Mock
	private ReasonAnalysis reasonAnalysis;
	@Mock
	private GuardRailAnalysis guardRailAnalysis;
	@InjectMocks
	private AnalysisService analysisService;

	@Mock
	private ScanData scanData;
	private MSGData msgData;

	@BeforeEach
	void setUp() {
		msgData = new MSGData();
		lenient().when(scanData.getMsgData()).thenReturn(msgData);
	}

	@Nested
	@DisplayName("ALLOW 액션 - 전체 분석 실행")
	class AllowAction {

		@BeforeEach
		void setup() {
			msgData.setAction("ALLOW");
		}

		@Test
		@DisplayName("모든 분석 단계가 순서대로 호출됨")
		void allSteps_shouldBeCalledInOrder() {
			analysisService.analyse(scanData);

			verify(checkWorkingDay).setDay(scanData);
			verify(reasonAnalysis).setReason(scanData);
			verify(attachAnalysis).setAttachText(scanData);
			verify(attachAnalysis).setAttachThumbnail(scanData);
			verify(keywordAnalysis).detect(scanData);
			verify(privacyAnalysis).detect(scanData);
			verify(guardRailAnalysis).detect(scanData);
			verify(userAgentAnalysis).detect(scanData);
		}

		@Test
		@DisplayName("checkWorkingDay 실패 → 나머지 단계 정상 실행")
		void workday_fail_othersContinue() {
			doThrow(new RuntimeException("workday error")).when(checkWorkingDay).setDay(scanData);
			analysisService.analyse(scanData);

			verify(reasonAnalysis).setReason(scanData);
			verify(attachAnalysis).setAttachText(scanData);
			verify(keywordAnalysis).detect(scanData);
			verify(privacyAnalysis).detect(scanData);
		}

		@Test
		@DisplayName("attachText 실패 → thumbnail, keyword, privacy 정상 실행")
		void attachText_fail_othersContinue() {
			doThrow(new RuntimeException("attach error")).when(attachAnalysis).setAttachText(scanData);
			analysisService.analyse(scanData);

			verify(attachAnalysis).setAttachThumbnail(scanData);
			verify(keywordAnalysis).detect(scanData);
			verify(privacyAnalysis).detect(scanData);
			verify(guardRailAnalysis).detect(scanData);
			verify(userAgentAnalysis).detect(scanData);
		}

		@Test
		@DisplayName("thumbnail 실패 → keyword 이후 정상 실행")
		void thumbnail_fail_othersContinue() {
			doThrow(new RuntimeException("thumbnail error")).when(attachAnalysis).setAttachThumbnail(scanData);
			analysisService.analyse(scanData);

			verify(keywordAnalysis).detect(scanData);
			verify(privacyAnalysis).detect(scanData);
			verify(guardRailAnalysis).detect(scanData);
		}

		@Test
		@DisplayName("keyword 실패 → privacy 이후 정상 실행")
		void keyword_fail_othersContinue() {
			doThrow(new RuntimeException("keyword error")).when(keywordAnalysis).detect(scanData);
			analysisService.analyse(scanData);

			verify(privacyAnalysis).detect(scanData);
			verify(guardRailAnalysis).detect(scanData);
			verify(userAgentAnalysis).detect(scanData);
		}

		@Test
		@DisplayName("privacy 실패 → guardRail, userAgent 정상 실행")
		void privacy_fail_othersContinue() {
			doThrow(new RuntimeException("privacy error")).when(privacyAnalysis).detect(scanData);
			analysisService.analyse(scanData);

			verify(guardRailAnalysis).detect(scanData);
			verify(userAgentAnalysis).detect(scanData);
		}

		@Test
		@DisplayName("guardRail 실패 → userAgent 정상 실행")
		void guardRail_fail_othersContinue() {
			doThrow(new RuntimeException("guardrail error")).when(guardRailAnalysis).detect(scanData);
			analysisService.analyse(scanData);

			verify(userAgentAnalysis).detect(scanData);
		}

		@Test
		@DisplayName("모든 ALLOW 단계가 실패해도 예외 전파 없음")
		void allFail_noException() {
			doThrow(new RuntimeException("1")).when(checkWorkingDay).setDay(scanData);
			doThrow(new RuntimeException("2")).when(reasonAnalysis).setReason(scanData);
			doThrow(new RuntimeException("3")).when(attachAnalysis).setAttachText(scanData);
			doThrow(new RuntimeException("4")).when(attachAnalysis).setAttachThumbnail(scanData);
			doThrow(new RuntimeException("5")).when(keywordAnalysis).detect(scanData);
			doThrow(new RuntimeException("6")).when(privacyAnalysis).detect(scanData);
			doThrow(new RuntimeException("7")).when(guardRailAnalysis).detect(scanData);
			doThrow(new RuntimeException("8")).when(userAgentAnalysis).detect(scanData);

			assertDoesNotThrow(() -> analysisService.analyse(scanData));
		}
	}

	@Nested
	@DisplayName("BLOCK 액션 - ALLOW 전용 분석 미실행")
	class BlockAction {

		@BeforeEach
		void setup() {
			msgData.setAction("BLOCK");
		}

		@Test
		@DisplayName("공통 단계(workday, reason)만 실행, ALLOW 전용 단계는 스킵")
		void block_shouldSkipAllowOnlySteps() {
			analysisService.analyse(scanData);

			verify(checkWorkingDay).setDay(scanData);
			verify(reasonAnalysis).setReason(scanData);

			verify(attachAnalysis, never()).setAttachText(scanData);
			verify(attachAnalysis, never()).setAttachThumbnail(scanData);
			verify(keywordAnalysis, never()).detect(scanData);
			verify(privacyAnalysis, never()).detect(scanData);
			verify(guardRailAnalysis, never()).detect(scanData);
			verify(userAgentAnalysis, never()).detect(scanData);
		}
	}

	private static void assertDoesNotThrow(Runnable runnable) {
		try {
			runnable.run();
		} catch (Exception e) {
			throw new AssertionError("Expected no exception but got: " + e.getMessage(), e);
		}
	}
}
