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
	private final PrivacyAnalysis privacyAnalysis;
	private final CheckWorkingDay checkWorkingDay;
	private final ReasonAnalysis reasonAnalysis;

	public void analyse(final ScanData data) {
		try {
			checkWorkingDay.setDay(data);                // 근무 시간, 근무 외 시간, 휴일, 년도의 주차 설정
			reasonAnalysis.setReason(data);              // 차단의 경우 사유를 파싱
			//networkGEOLocation.networkGEO(data);       // source ip, dest ip MAXMIND 유틸을 활용하여 국가 탐지 * 현재는 사용하지 않음
			//bodyLanguage.detect(data);                 // 본문 텍스트의 국가 탐지 (최대 2000자 기준, 나머지는 자르고 탐지) * 현재는 사용하지 않음

			if (Common.isEquals(data.getMsgData().getAction(), "ALLOW")) { //허용인 경우만 실행
				attachAnalysis.setAttachText(data);      // 첨부 암호여부, 압축 파일목록, 텍스트 추출, 텍스트 추출 후 관련 분석 기능 실행 필수!! (모든 파일)
				attachAnalysis.setAttachThumbnail(data); // 파일의 썸네일 생성
				keywordAnalysis.detect(data);            // 키워드 탐지
				privacyAnalysis.detect(data);            // 개인정보 탐지
				userAgentAnalysis.detect(data);          // 사용자의 OS, Agent 정보를 탐지 및 추가
			}
		} catch (Exception e) {
			log.warn("ANALYSE | {}", e.getMessage());
			log.error("", e);
		}
	}
}
