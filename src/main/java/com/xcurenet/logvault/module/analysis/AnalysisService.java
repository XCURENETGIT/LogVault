package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.util.CheckWorkingDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class AnalysisService {
	//private final NetworkGEOLocation networkGEOLocation;
	//private final BodyLanguage bodyLanguage;
	private final AttachAnalysis attachAnalysis;
	private final UserAgentAnalysis userAgentAnalysis;
	private final KeywordAnalysis keywordAnalysis;
	private final PrivacyAIAnalysis privacyAnalysis;
	private final CheckWorkingDay checkWorkingDay;
	private final ReasonAnalysis reasonAnalysis;
	private final GuardRailAnalysis guardRailAnalysis;
	private final AnomalyScoreCalculator anomalyScoreCalculator;

	/**
	 * 분석 파이프라인을 실행한다.
	 * <p>
	 * 각 분석 단계는 독립적으로 try-catch 처리되어, 특정 단계가 실패하더라도
	 * 나머지 단계는 정상적으로 수행된다. (예: 썸네일 생성 실패가 키워드 탐지를 막지 않음)
	 * <p>
	 * 실행 순서:
	 * <pre>
	 *   [공통]  checkWorkingDay → reasonAnalysis
	 *   [ALLOW] attachText → thumbnail → keyword → privacy → guardRail → userAgent
	 * </pre>
	 * ※ attachText는 후속 분석(keyword, privacy 등)에 텍스트를 제공하므로 가장 먼저 실행.
	 *   attachText 실패 시에도 본문(body) 텍스트 기반의 키워드/개인정보 탐지는 수행 가능.
	 */
	public void analyse(final ScanData data) {
		// [공통] 근무 시간, 휴일, 주차 설정
		try {
			checkWorkingDay.setDay(data);
		} catch (Exception e) {
			log.warn("ANALYSE_WORKDAY | {}", e.getMessage(), e);
		}

		// [공통] 차단 사유 파싱
		try {
			reasonAnalysis.setReason(data);
		} catch (Exception e) {
			log.warn("ANALYSE_REASON | {}", e.getMessage(), e);
		}

		//networkGEOLocation.networkGEO(data);       // source ip, dest ip MAXMIND 유틸을 활용하여 국가 탐지 * 현재는 사용하지 않음
		//bodyLanguage.detect(data);                 // 본문 텍스트의 국가 탐지 (최대 2000자 기준, 나머지는 자르고 탐지) * 현재는 사용하지 않음

		if (!Common.isEquals(data.getMsgData().getAction(), "ALLOW")) return; // ALLOW가 아니면 이하 분석 생략

		// 첨부 텍스트 추출 (후속 분석에 텍스트를 제공하므로 가장 먼저 실행)
		try {
			attachAnalysis.setAttachText(data);
		} catch (Exception e) {
			log.warn("ANALYSE_ATTACH_TEXT | {}", e.getMessage(), e);
		}

		// 첨부 썸네일 생성
		try {
			attachAnalysis.setAttachThumbnail(data);
		} catch (Exception e) {
			log.warn("ANALYSE_THUMBNAIL | {}", e.getMessage(), e);
		}

		// 키워드 탐지
		try {
			keywordAnalysis.detect(data);
		} catch (Exception e) {
			log.warn("ANALYSE_KEYWORD | {}", e.getMessage(), e);
		}

		// 개인정보 탐지
		try {
			privacyAnalysis.detect(data);
		} catch (Exception e) {
			log.warn("ANALYSE_PRIVACY | {}", e.getMessage(), e);
		}

		// GuardRail 탐지
		try {
			guardRailAnalysis.detect(data);
		} catch (Exception e) {
			log.warn("ANALYSE_GUARDRAIL | {}", e.getMessage(), e);
		}

		// User-Agent 분석 (OS, 브라우저, 디바이스)
		try {
			userAgentAnalysis.detect(data);
		} catch (Exception e) {
			log.warn("ANALYSE_USERAGENT | {}", e.getMessage(), e);
		}

		// 이상행위 점수 계산 (모든 분석 완료 후 최종 단계에서 실행)
		try {
			anomalyScoreCalculator.calculate(data);
		} catch (Exception e) {
			log.warn("ANALYSE_ANOMALY_SCORE | {}", e.getMessage(), e);
		}
	}
}
