package com.xcurenet.common.utils;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class LangDetectUtil {

	private static final Map<Language, String> COUNTRY_MAP = new HashMap<>();
	private static final int MAX_TEXT_LENGTH = 2000; // 최대 텍스트 길이 제한 (예: 2000자)

	static {
		COUNTRY_MAP.put(Language.BASQUE, "ES");     // 스페인 (Spain)
		COUNTRY_MAP.put(Language.CATALAN, "ES");    // 스페인 (Spain)
		COUNTRY_MAP.put(Language.SPANISH, "ES");    // 스페인 (Spain)
		COUNTRY_MAP.put(Language.CHINESE, "CN");    // 중국 (China)
		COUNTRY_MAP.put(Language.DANISH, "DK");     // 덴마크 (Denmark)
		COUNTRY_MAP.put(Language.DUTCH, "NL");      // 네덜란드 (Netherlands)
		COUNTRY_MAP.put(Language.ENGLISH, "US");    // 미국 (United Kingdom)
		COUNTRY_MAP.put(Language.FRENCH, "FR");     // 프랑스 (France)
		COUNTRY_MAP.put(Language.GERMAN, "DE");     // 독일 (Germany)
		COUNTRY_MAP.put(Language.GREEK, "GR");      // 그리스 (Greece)
		COUNTRY_MAP.put(Language.HINDI, "IN");      // 인도 (India)
		COUNTRY_MAP.put(Language.GUJARATI, "IN");   // 인도 (India)
		COUNTRY_MAP.put(Language.MARATHI, "IN");    // 인도 (India)
		COUNTRY_MAP.put(Language.TAMIL, "IN");      // 인도 (India)
		COUNTRY_MAP.put(Language.TELUGU, "IN");     // 인도 (India)
		COUNTRY_MAP.put(Language.INDONESIAN, "ID"); // 인도네시아 (Indonesia)
		COUNTRY_MAP.put(Language.IRISH, "IE");      // 아일랜드 (Ireland)
		COUNTRY_MAP.put(Language.ITALIAN, "IT");    // 이탈리아 (Italy)
		COUNTRY_MAP.put(Language.JAPANESE, "JP");   // 일본 (Japan)
		COUNTRY_MAP.put(Language.KOREAN, "KR");     // 한국 (South Korea)
		COUNTRY_MAP.put(Language.MALAY, "MY");      // 말레이시아 (Malaysia)
		COUNTRY_MAP.put(Language.MAORI, "NZ");      // 뉴질랜드 (New Zealand)
		COUNTRY_MAP.put(Language.PORTUGUESE, "PT"); // 포르투갈 (Portugal)
		COUNTRY_MAP.put(Language.RUSSIAN, "RU");    // 러시아 (Russia)
		COUNTRY_MAP.put(Language.SWEDISH, "SE");    // 스웨덴 (Sweden)
		COUNTRY_MAP.put(Language.TAGALOG, "PH");    // 필리핀 (Philippines)
		COUNTRY_MAP.put(Language.THAI, "TH");       // 태국 (Thailand)
		COUNTRY_MAP.put(Language.TURKISH, "TR");    // 터키 (Turkey)
		COUNTRY_MAP.put(Language.VIETNAMESE, "VN"); // 베트남 (Vietnam)
		COUNTRY_MAP.put(Language.WELSH, "GB");      // 영국 (United Kingdom)
		COUNTRY_MAP.put(Language.UNKNOWN, "UN");    // 알 수 없음 (Unknown)
	}

	private final LanguageDetector detector;

	/**
	 * 텍스트의 언어를 감지하는 메서드
	 *
	 * @param text 텍스트
	 * @return 텍스트 탐지 언어
	 */
	public String detectLanguage(String text) throws IllegalArgumentException {
		if (text.length() > MAX_TEXT_LENGTH) {
			log.warn("[DETECT] Text is too large. Limiting input to {} characters.", MAX_TEXT_LENGTH);
			text = text.substring(0, MAX_TEXT_LENGTH); // 텍스트를 자름
		}

		long startTime = System.currentTimeMillis();
		String lang = COUNTRY_MAP.getOrDefault(detector.detectLanguageOf(text), "UN");
		long endTime = System.currentTimeMillis();
		log.debug("[DETECT] Language time : {} ms", endTime - startTime);
		return lang;
	}

	// 언어와 해당 언어에 대한 신뢰도를 포함하는 Map<Language, Double> 형태의 결과값 출력
	public Map<Language, Double> computeLanguage(String text) {
		return detector.computeLanguageConfidenceValues(text);
	}
}
