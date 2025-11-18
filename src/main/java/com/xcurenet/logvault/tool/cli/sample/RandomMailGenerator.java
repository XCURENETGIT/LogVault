package com.xcurenet.logvault.tool.cli.sample;

import java.util.*;

public class RandomMailGenerator {

	private static final Random RANDOM = new Random();

	private static final String[] OPENING = {"안녕하세요.", "좋은 아침입니다.", "바쁘신 일정에도 항상 감사드립니다.", "문의 주신 내용 관련하여 안내드립니다.", "지난 회의에서 요청하신 내역 공유드립니다.", "요청하신 작업 내용 검토 후 회신드립니다.", "해당 건 관련하여 다시 한번 정리하여 전달드립니다.", "바쁘신 와중에 메일 드립니다.", "추가 요청 사항이 있어 연락드립니다.", "저희 쪽에서 확인한 내용 전달드립니다.", "문의하신 부분에 대해 내부 검토를 완료했습니다.", "관련 사항 업데이트가 있어 공유드립니다.", "지난번 건 관련하여 후속 안내드립니다.", "처리 결과에 대한 상세 내용을 전달드립니다.", "파일 검토 후 몇 가지 추가 확인이 필요하여 연락드립니다."};

	private static final String[] BODY_SENTENCES = {
			// 요청 관련
			"요청하신 자료는 첨부파일로 전달드립니다.", "요청하신 내역은 검토 후 별도로 회신드리겠습니다.", "요청하신 설정 변경 작업은 금일 중 처리 예정입니다.", "추가로 필요한 부분 있으시면 언제든지 문의 부탁드립니다.",

			// 보고/업데이트
			"현재까지 확인된 내용은 아래와 같습니다.", "해당 이슈는 내부 개발팀과 협의 중에 있습니다.", "문제 현상은 재현 테스트에서 동일하게 발생 중입니다.", "관련 데이터를 수집하여 다시 전달드리겠습니다.", "현재 원인 분석을 진행하고 있으며, 결과는 다시 안내드리겠습니다.", "로그 분석 결과 특정 시점 이후 오류가 발생한 것으로 확인됩니다.", "현재 상황은 정상으로 보이나 추가 모니터링이 필요합니다.", "금일 기준으로 진행 상황은 80% 정도 완료되었습니다.",

			// 일정
			"작업 일정은 계획대로 진행될 예정입니다.", "일정 변경 사항이 있어 공유드립니다.", "검토 일정은 내부 상황에 따라 다소 조정될 수 있습니다.", "해당 작업은 내일 오전 중으로 완료 예정입니다.", "다음 주 중으로 테스트를 진행할 계획입니다.",

			// 첨부 관련
			"관련 자료는 첨부된 파일을 참고 부탁드립니다.", "첨부파일이 정상적으로 업로드되지 않아 다시 전달드립니다.", "첨부된 문서 3페이지에 상세 내용이 정리되어 있습니다.",

			// 오류/장애
			"해당 오류는 서버 재기동 후에는 발생하지 않는 것으로 확인되었습니다.", "일시적인 네트워크 지연으로 인해 실패한 것으로 추정됩니다.", "로그 상으로는 인증 과정에서 오류가 발생한 것으로 보입니다.", "추가적인 디버깅 로그 수집이 필요합니다.",

			// 안내
			"관련 기능은 다음 패치 버전에 반영될 예정입니다.", "현재 버전에서는 해당 기능이 제공되지 않고 있습니다.", "정책에 따라 접근 권한이 제한될 수 있습니다.", "개발팀 검토 결과 정상 동작으로 확인되었습니다.",

			// 협조 요청
			"정확한 확인을 위해 테스트 환경 정보를 공유 부탁드립니다.", "문제 확인을 위해 추가 로그 수집을 부탁드립니다.", "사용 중이신 버전 정보를 전달해주시면 감사하겠습니다.", "재현 경로를 상세하게 공유해주시면 분석에 도움이 됩니다."};

	private static final String[] CLOSING = {"감사합니다.", "확인 부탁드립니다.", "좋은 하루 되세요.", "궁금하신 부분 있으시면 언제든지 문의 바랍니다.", "회신 기다리겠습니다.", "검토 부탁드립니다.", "빠른 확인 부탁드립니다.", "확인 후 연락 부탁드립니다.", "언제든지 편하게 연락 주세요.", "추가 문의가 있으시면 답장 부탁드립니다.", "도움이 되셨길 바랍니다.", "빠른 처리 감사드립니다.", "앞으로도 잘 부탁드립니다.", "협조에 감사드립니다."};

	private static final String[] SIGNATURE = {"솔루션개발팀 정경수 드림", "XCURENET 개발팀", "기술지원팀", "보안연구팀", "R&D 센터", "운영지원팀", "고객지원센터", "Infra Engineering Team", "Service Platform Team", "Cloud Operations Team"};

	/**
	 * 랜덤 메일 생성
	 *
	 * @param minSentences 최소 문장수
	 * @param maxSentences 최대 문장수
	 */
	public static String generateMail(int minSentences, int maxSentences) {
		int count = RANDOM.nextInt(maxSentences - minSentences + 1) + minSentences;
		return generateMail(count);
	}

	/**
	 * 기존 방식
	 */
	public static String generateMail(int sentenceCount) {
		StringBuilder sb = new StringBuilder();

		sb.append(random(OPENING)).append("\n\n");

		for (int i = 0; i < sentenceCount; i++) {
			sb.append("- ").append(random(BODY_SENTENCES)).append("\n");
		}

		sb.append("\n").append(random(CLOSING)).append("\n\n");
		sb.append(random(SIGNATURE));

		return sb.toString();
	}

	private static String random(String[] arr) {
		return arr[RANDOM.nextInt(arr.length)];
	}

	public static void main(String[] args) {
		System.out.println(generateMail(5, 10)); // 5~10개 문장 랜덤
	}
}
