package com.xcurenet.common.utils;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangDetectConfig {

	@Bean
	public LanguageDetector languageDetector() {
		return LanguageDetectorBuilder.fromLanguages(
						Language.SPANISH, Language.BASQUE, Language.CATALAN, Language.CHINESE, Language.DANISH, Language.DUTCH,
						Language.ENGLISH, Language.FRENCH, Language.GERMAN, Language.GREEK, Language.HINDI, Language.GUJARATI,
						Language.MARATHI, Language.TAMIL, Language.TELUGU, Language.INDONESIAN, Language.ITALIAN, Language.IRISH,
						Language.JAPANESE, Language.KOREAN, Language.MALAY, Language.MAORI, Language.PORTUGUESE, Language.RUSSIAN,
						Language.SWEDISH, Language.TAGALOG, Language.THAI, Language.TURKISH, Language.VIETNAMESE, Language.WELSH,
						Language.UNKNOWN)
				.build();
	}
}
