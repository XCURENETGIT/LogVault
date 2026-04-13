package com.xcurenet.logvault.loader;

import com.xcurenet.logvault.loader.mapper.AnomalyScoreMapper;
import com.xcurenet.logvault.loader.type.AnomalyScoreVO;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UI_ANOMALY_SCORE 테이블에서 (MAPR_TABLE, TARGET_ID) → ANOMALY_LEVEL_CD 매핑을 로드하고,
 * 레벨별 점수는 PropertySourceLoader 가 Environment 에 올려둔 값을 사용한다.
 */
@Log4j2
@Service
public class AnomalyScoreLoader {

    private static final String KEY_SEPARATOR = "::";

    public static final String TABLE_GUARD_RAIL = "UI_GUARD_RAIL";
    public static final String TABLE_KEYWORD_CATEGORY = "UI_KEYWORD_CATEGORY";
    public static final String TABLE_PATTERN = "UI_PATTERN";

    private final AnomalyScoreMapper mapper;

    private final AtomicReference<Map<String, String>> SCORE_LEVEL_REF = new AtomicReference<>(Collections.emptyMap());

    /**
     * ANOMALY_LEVEL_CD → score (PropertySourceLoader 에서 로드한 값)
     */
    private final Map<String, Integer> levelScoreMap;

    public AnomalyScoreLoader(AnomalyScoreMapper mapper,
                              @Value("${anomaly.high.score:0}") int highScore,
                              @Value("${anomaly.mid.score:0}") int midScore,
                              @Value("${anomaly.low.score:0}") int lowScore) {
        this.mapper = mapper;
        this.levelScoreMap = Map.of("HIGH", highScore, "MID", midScore, "LOW", lowScore);
        log.info("INFO_LOAD | AnomalyScore LevelScore (from Environment): {}", levelScoreMap);
    }

    public void load() {
        List<AnomalyScoreVO> scoreList = mapper.getAnomalyScoreList();
        Map<String, String> levelMap = new ConcurrentHashMap<>();
        for (AnomalyScoreVO vo : scoreList) {
            String key = compositeKey(vo.getMapperTable(), vo.getTargetId());
            levelMap.put(key, vo.getAnomalyLevelCd());
            log.info("INFO_LOAD | AnomalyScore: {} → {}", key, vo.getAnomalyLevelCd());
        }
        SCORE_LEVEL_REF.set(levelMap);

        log.info("INFO_LOAD | AnomalyScore Entries:{} | LevelScore:{}", levelMap.size(), levelScoreMap);
    }

    /**
     * (MAPR_TABLE, TARGET_ID) 조합으로 점수를 반환한다.
     *
     * @param mapperTable UI_GUARD_RAIL, UI_KEYWORD_CATEGORY, UI_PATTERN
     * @param targetId    가드레일 카테고리, 키워드 SEQ, 패턴 코드
     * @return 점수 (설정이 없으면 0)
     */
    public int getScore(String mapperTable, String targetId) {
        String key = compositeKey(mapperTable, targetId);
        String level = SCORE_LEVEL_REF.get().get(key);
        if (level == null) return 0;
        return levelScoreMap.getOrDefault(level, 0);
    }

    private String compositeKey(String mapperTable, String targetId) {
        return mapperTable + KEY_SEPARATOR + targetId;
    }
}
