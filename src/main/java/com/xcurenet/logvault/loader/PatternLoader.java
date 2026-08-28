package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.PatternInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@Getter
@Log4j2
@Service
@RequiredArgsConstructor
public class PatternLoader {

	private static final AtomicReference<Map<String, String>> DETECT_CODE_MAP_REF = new AtomicReference<>(Map.of());

	private final InfoLoaderService infoLoaderService;

	public void load() {
		long version = infoLoaderService.getPatternVersion();
		List<PatternInfo> datas = infoLoaderService.getPatternInfo(version);
		log.info("INFO_LOAD | Rule Version : {} | Pattern Size: {}", version, datas.size());

		Map<String, String> patterns = new LinkedHashMap<>();
		for (PatternInfo item : datas) {
			log.debug("INFO_LOAD | Pattern: {}", item);
			if (item == null || Common.isEquals(item.getUseYn(), "N")) continue;

			patterns.put(item.getPatternCd(), item.getPatternType());
		}

		DETECT_CODE_MAP_REF.set(patterns);
	}

	public static boolean isDetectCode(String code) {
		return DETECT_CODE_MAP_REF.get().containsKey(code);
	}

	public static String getPatternType(String code) {
		return code == null ? null : DETECT_CODE_MAP_REF.get().get(code);
	}

	public static boolean isPrivacyCode(String code) {
		return Common.isEquals("N", Common.nvl(getPatternType(code)).trim().toUpperCase());
	}

	public static boolean isSensitiveCode(String code) {
		return Common.isEquals("S", Common.nvl(getPatternType(code)).trim().toUpperCase());
	}

}
