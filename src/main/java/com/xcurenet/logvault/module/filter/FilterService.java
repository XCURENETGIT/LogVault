package com.xcurenet.logvault.module.filter;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.exception.FilterException;
import com.xcurenet.logvault.exception.IndexerException;
import com.xcurenet.logvault.loader.ServiceLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class FilterService {
	private final Config config;
	private final ServiceLoader serviceLoader;

	/**
	 * 메시지의 필터링 여부를 판단한다.
	 * <p>
	 * 필터링 흐름 (return true = 필터링 → 색인하지 않음, return false = 통과 → 파이프라인 계속 진행):
	 * <pre>
	 *   1. BLOCK 액션       → 무조건 통과 (false). 차단 이력은 분석 없이 색인만 수행.
	 *                         ※ AnalysisService에서 ALLOW일 때만 분석(텍스트 추출, 키워드, 개인정보 등)을 수행하므로
	 *                            BLOCK은 자연스럽게 분석이 생략되고 색인·파일전송·로그만 처리된다.
	 *   2. 로깅 대상 서비스  → ServiceLoader에 등록되지 않은 서비스는 필터링 (true)
	 *   3. SVC 코드 null     → 필터링 (true)
	 *   4. SVC 접두사 "I"    → "I"로 시작하지 않으면 필터링 (true). 생성형 AI 서비스만 처리 대상.
	 *   5. Unknown 서비스    → filter.service.unknown=true이면 "IUKU" 필터링 (true)
	 *   6. 본문·첨부 모두 없음 → 필터링 (true)
	 *   7. 위 조건 모두 통과  → 통과 (false)
	 * </pre>
	 *
	 * @param data 스캔 데이터 (파싱 완료된 MSG + EmassDoc)
	 * @return true: 필터링 대상 (색인하지 않음), false: 파이프라인 계속 진행
	 * @throws FilterException 필터링 중 예외 발생 시
	 */
	public boolean filter(final ScanData data) throws FilterException {
		MSGData msg = data.getMsgData();
		boolean rs;
		try {
			// [1] BLOCK: 차단 메시지는 필터링하지 않고 파이프라인으로 진행 (분석은 AnalysisService에서 ALLOW 조건으로 자동 생략됨)
			if (Common.isEquals(msg.getAction(), "BLOCK")) return false;

			// [2] 로깅 대상 서비스: ServiceLoader에 등록된 서비스(svc12)만 통과
			if (!isLoggingService(data.getEmassDoc())) {
				log.info("FILT_SVC | {}", msg.getSvc());
				return true;
			}

			// [3] SVC 코드 null: 서비스 유형을 판별할 수 없는 메시지 필터링
			if (msg.getSvc() == null) return true;

			// [4] SVC 접두사: 생성형 AI 서비스("I"로 시작)만 처리 대상
			rs = !Common.nvl(msg.getSvc()).startsWith("I");
			if (rs) {
				log.info("FILT_SVC | {}", msg.getSvc());
				return true;
			}

			// [5] Unknown 서비스: 설정에 따라 "IUKU"(Unknown) 서비스 필터링
			if (config.isFilterServiceUnknown()) {
				rs = Common.isEquals(msg.getSvc(), "IUKU");
				if (rs) {
					log.info("FILT_SVC | {}", msg.getSvc());
					return true;
				}
			}

			// [6] 본문·첨부 모두 없음: 분석할 컨텐츠가 없으므로 필터링
			if (Common.isEmpty(msg.getMsgFile()) && msg.getAppFile().isEmpty()) {
				log.info("FILT_SVC | body is empty, attach is empty : {}", msg.getSvc());
				return true;
			}
		} catch (Exception e) {
			throw ExFactory.ex(FilterException::new, ErrorCode.FILTER_FAIL, Map.of("svc", msg.getSvc()), e);
		}
		// [7] 모든 조건 통과: 파이프라인 계속 진행 (분석 → 색인 → 알림)
		return false;
	}

	private boolean isLoggingService(final EmassDoc doc) {
		if (doc == null || doc.getService() == null) return false;
		return serviceLoader.contains(doc.getService().getSvc12());
	}
}
