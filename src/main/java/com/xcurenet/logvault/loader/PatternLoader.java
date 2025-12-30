package com.xcurenet.logvault.loader;

import com.xcurenet.common.regex.DetectOptions;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.PatternInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;


@Getter
@Log4j2
@Service
@RequiredArgsConstructor
public class PatternLoader {

	private static final AtomicReference<Map<String, Integer>> DETECT_CODE_MAP_REF = new AtomicReference<>();
	//private static final AtomicReference<PatternDetector> USER_CODE_MAP_REF = new AtomicReference<>();
	public final AtomicReference<Set<String>> PATTERN_ALARM_REF = new AtomicReference<>();
	public final AtomicReference<Set<String>> PATTERN_SYSLOG_REF = new AtomicReference<>();

	private final InfoLoaderService infoLoaderService;

	public void load() {
		List<PatternInfo> datas = infoLoaderService.getPatternInfo();
		log.info("INFO_LOAD | Pattern Size: {}", datas.size());

		Map<String, Integer> fresh = new LinkedHashMap<>();
		Map<String, DetectOptions> user = new LinkedHashMap<>();
		Set<String> alarmSet = new HashSet<>();
		Set<String> syslogSet = new HashSet<>();
		for (PatternInfo item : datas) {
			if (item == null || Common.isEquals(item.getUseYn(), "N")) continue;

			if (Common.isEquals(item.getPatternType(), "N")) { // 미리 정의된 패턴 (주민번호, 운전면허번호 등)
				fresh.put(item.getPatternCd(), item.getMinCount());
			} else { // 사용자 정의 패턴 (현재는 사용하지 않음, 추후 사용 예정)
				if (item.getRegex() == null) continue;
				String pattern = StringEscapeUtils.unescapeJava(item.getRegex());
				Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.UNIX_LINES);
				user.put(item.getPatternCd(), DetectOptions.builder().key(item.getPatternCd()).pattern(item.getRegex()).compile(p).minCount(item.getMinCount()).build());
				log.info("INFO_LOAD | ADD Custom Pattern: {} | {} | {}", item.getPatternCd(), pattern, item.getMinCount());
			}
			if (Common.isEquals(item.getAlarmYn(), "Y")) {
				alarmSet.add(item.getPatternCd());
			}
			if (Common.isEquals(item.getSyslogYn(), "Y")) {
				syslogSet.add(item.getPatternCd());
			}
		}

		DETECT_CODE_MAP_REF.set(Collections.unmodifiableMap(fresh));
		//USER_CODE_MAP_REF.set(new PatternDetector(user));
		PATTERN_ALARM_REF.set(Collections.unmodifiableSet(alarmSet));
		PATTERN_ALARM_REF.set(Collections.unmodifiableSet(syslogSet));
	}

	public static Map<String, Integer> getDetectCodeMap() {
		return DETECT_CODE_MAP_REF.get();
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

	public Set<String> getPatternAlert() {
		return PATTERN_ALARM_REF.get();
	}

	public Set<String> getPatternSyslog() {
		return PATTERN_ALARM_REF.get();
	}
}
