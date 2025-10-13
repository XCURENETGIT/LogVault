package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.DateUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;


@Log4j2
@Service
@RequiredArgsConstructor
public class InfoLoader {

	private final UserLoader userLoader;
	private final HoliDayLoader holiDayLoader;
	private final WorkDayLoader workDayLoader;
	private final KeywordLoader keywordLoader;

	@PostConstruct
	public void init() {
		load();
	}

	private void load() {
		long startTime = System.currentTimeMillis();
		log.info("[INFO_LOAD] START");

		log.debug("[INFO_LOAD] UserInfo START");
		synchronized (this) {
			userLoader.load();
		}

		log.debug("[INFO_LOAD] HoliDay START");
		synchronized (this) {
			holiDayLoader.load();
		}

		log.debug("[INFO_LOAD] WorkDay START");
		synchronized (this) {
			workDayLoader.load();
		}

		log.debug("[INFO_LOAD] Keyword START");
		synchronized (this) {
			keywordLoader.load();
		}

		log.info("[INFO_LOAD] END | {}\n", DateUtils.duration(startTime));
	}
}
