package com.xcurenet.logvault.module.analysis;

import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class AnalysisService {
	private final NetworkGEOLocation networkGEOLocation;
	private final BodyLanguage bodyLanguage;
	private final AttachAnalysis attachAnalysis;
	private final UserAgentAnalysis userAgentAnalysis;

	public void analyse(final ScanData data) {
		try {
			networkGEOLocation.networkGEO(data);         // source ip, dest ip MAXMIND 유틸을 활용하여 국가 탐지
			bodyLanguage.detect(data);                   // 본문 텍스트의 국가 탐지 (최대 2000자 기준, 나머지는 자르고 탐지)
			attachAnalysis.expectAttachExtension(data);  // 첨부 예상 확장자 탐지 (파일이 존재하며 Decoder 에서 확장자를 주지 않았을 경우)
			attachAnalysis.setAttachText(data);          // 첨부 암호여부, 압축 파일목록, 텍스트 추출 (모든 파일)
			userAgentAnalysis.detect(data);              // 사용자의 OS, Agent 정보를 탐지 및 추가
		} catch (Exception e) {
			log.warn("[ANALYSE] {} | {}", data.getEmassDoc().getMsgid(), e.getMessage());
			log.error("", e);
		}
	}
}
