package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.DateUtils;
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
	private final ServiceLoader serviceLoader;
	private final WorkDayLoader workDayLoader;
	private final AnomalyScoreLoader anomalyScoreLoader;
	private final RuleLoader ruleLoader;
    private final GuardRailLoader guardRailLoader;
    private final AiServiceLoader aiServiceLoader;
    private final AccountsLoader accountsLoader;
    private final ImageCategoryLoader imageCategoryLoader;

	public void init() {
		StopWatch sw = DateUtils.start();
		log.info("INFO_LOAD | START");

		userLoad();
		keywordLoad();
		patternLoad();
		serviceLoad();
		workDayLoad();
		anomalyScoreLoad();
		ruleLoad();
        guardRailLoad();
        aiServiceLoad();
		companyAccountLoad();
		imageCategoryLoad();

		log.info("INFO_LOAD | END | {}\n", DateUtils.stop(sw));
	}

	public void userLoad() {
		log.debug("INFO_LOAD | UserInfo START");
		synchronized (this) {
			userLoader.load();
		}
	}

	public void keywordLoad() {
		log.debug("INFO_LOAD | Keyword START");
		synchronized (this) {
			keywordLoader.load();
		}
	}

	public void patternLoad() {
		log.debug("INFO_LOAD | Pattern START");
		synchronized (this) {
			patternLoader.load();
		}
	}

	public void serviceLoad() {
		log.debug("INFO_LOAD | Service START");
		synchronized (this) {
			serviceLoader.load();
		}
	}

	public void workDayLoad() {
		log.debug("INFO_LOAD | WORKDAY START");
		synchronized (this) {
			workDayLoader.load();
		}
	}

	public void anomalyScoreLoad() {
		log.debug("INFO_LOAD | AnomalyScore START");
		synchronized (this) {
			anomalyScoreLoader.load();
		}
	}

	public void ruleLoad() {
		log.debug("INFO_LOAD | Rule START");
		synchronized (this) {
			ruleLoader.load();
		}
	}

	public void guardRailLoad() {
		log.debug("INFO_LOAD | GuardRail START");
		synchronized (this) {
            guardRailLoader.load();
		}
	}

    public void aiServiceLoad() {
        log.debug("INFO_LOAD | AiService START");
        synchronized (this) {
            aiServiceLoader.load();
        }
    }

	public void companyAccountLoad() {
		log.debug("INFO_LOAD | Company Accounts START");
		synchronized (this) {
			accountsLoader.load();
		}
	}

	public void imageCategoryLoad() {
		log.debug("INFO_LOAD | ImageCategory START");
		synchronized (this) {
			imageCategoryLoader.load();
		}
	}

}
