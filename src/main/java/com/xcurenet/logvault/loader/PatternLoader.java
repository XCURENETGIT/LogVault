package com.xcurenet.logvault.loader;

import com.xcurenet.common.regex.DetectOptions;
import com.xcurenet.common.regex.PatternDetector;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.PatternInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;


@Getter
@Log4j2
@Service
@RequiredArgsConstructor
public class PatternLoader {

	private static final AtomicReference<Map<String, Integer>> DETECT_CODE_MAP_REF = new AtomicReference<>();
	private static final AtomicReference<PatternDetector> USER_CODE_MAP_REF = new AtomicReference<>();

	private final InfoLoaderMapper mapper;

	public void load() {
		List<PatternInfo> datas = mapper.getPatternInfo();
		log.info("[INFO_LOAD] Pattern Size: {}", datas.size());

		Map<String, Integer> fresh = new LinkedHashMap<>();
		Map<String, DetectOptions> user = new LinkedHashMap<>();
		for (PatternInfo item : datas) {
			if (item == null) continue;

			if (CommonUtil.isEquals(item.getPatternType(), "N")) fresh.put(item.getPatternCd(), item.getMinCount());
			else {
				String pattern = StringEscapeUtils.unescapeJava(item.getRegex());
				Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.UNIX_LINES);
				user.put(item.getPatternCd(), DetectOptions.builder().key(item.getPatternCd()).pattern(item.getRegex()).compile(p).minCount(item.getMinCount()).build());
				log.info("[INFO_LOAD] ADD Custom Pattern: {} | {} | {}", item.getPatternCd(), pattern, item.getMinCount());
			}
		}
		DETECT_CODE_MAP_REF.set(Collections.unmodifiableMap(fresh));
		USER_CODE_MAP_REF.set(new PatternDetector(user));
	}

	public static Map<String, Integer> getDetectCodeMap() {
		return DETECT_CODE_MAP_REF.get();
	}

	public static PatternDetector getUserCodeMap() {
		return USER_CODE_MAP_REF.get();
	}

	public static boolean isDetectCode(String code) {
		return DETECT_CODE_MAP_REF.get().containsKey(code);
	}

	public static Integer getCodeValue(String code) {
		return DETECT_CODE_MAP_REF.get().get(code);
	}

	public static int getCodeValueOrDefault(String code, int defaultValue) {
		return DETECT_CODE_MAP_REF.get().getOrDefault(code, defaultValue);
	}
}
