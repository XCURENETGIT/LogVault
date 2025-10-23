package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.DateUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;


@Log4j2
@Service
@RequiredArgsConstructor
public class InfoLoader {

	private final UserLoader userLoader;
	private final KeywordLoader keywordLoader;
	private final PatternLoader patternLoader;

	@PostConstruct
	public void init() {
		StopWatch sw = DateUtils.start();
		log.info("[INFO_LOAD] START");

		userLoad();
		keywordLoad();
		patternLoad();

		log.info("[INFO_LOAD] END | {}\n", DateUtils.stop(sw));
	}

	public void userLoad() {
		log.debug("[INFO_LOAD] UserInfo START");
		synchronized (this) {
			userLoader.load();
		}
	}

	public void keywordLoad() {
		log.debug("[INFO_LOAD] Keyword START");
		synchronized (this) {
			keywordLoader.load();
		}
	}

	public void patternLoad() {
		log.debug("[INFO_LOAD] Pattern START");
		synchronized (this) {
			patternLoader.load();
		}
	}

	public void confLoad() {
		log.debug("[INFO_LOAD] Config START");
		synchronized (this) {
			patternLoader.load();
		}
	}
}
