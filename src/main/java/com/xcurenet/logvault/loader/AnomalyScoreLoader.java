package com.xcurenet.logvault.loader;

import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.AnomalyScoreVO;
import com.xcurenet.logvault.loader.type.DocumentSimilarityVO;
import com.xcurenet.logvault.loader.type.GuardRailVO;
import com.xcurenet.logvault.loader.type.PatternInfo;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UI_ANOMALY_SCORE 룰 히스토리에서 (MAPR_TABLE, TARGET_ID) → ANOMALY_LEVEL_CD 매핑을 로드하고,
 * 레벨별 점수는 PropertySourceLoader 가 Environment 에 올려둔 값을 사용한다.
 */
@Log4j2
@Service
public class AnomalyScoreLoader {

    private static final String KEY_SEPARATOR = "::";

    public static final String TABLE_GUARD_RAIL = "UI_GUARD_RAIL";
    public static final String TABLE_KEYWORD_CATEGORY = "UI_KEYWORD_CATEGORY";
    public static final String TABLE_PATTERN = "UI_PATTERN";
    public static final String TABLE_IMAGE_CATEGORY = "UI_IMAGE_CATEGORY";
    public static final String TABLE_DOCUMENT_SIMILARITY = "UI_DOCUMENT_SIMILARITY";

    private final InfoLoaderService infoLoaderService;

    private final AtomicReference<Map<String, String>> SCORE_LEVEL_REF = new AtomicReference<>(Collections.emptyMap());
    private final AtomicReference<Set<String>> ENABLED_TARGET_REF = new AtomicReference<>(Collections.emptySet());
    private final AtomicReference<Map<String, String>> DOCUMENT_NAME_REF = new AtomicReference<>(Collections.emptyMap());

    /**
     * ANOMALY_LEVEL_CD → score (PropertySourceLoader 에서 로드한 값)
     */
    private final Map<String, Integer> levelScoreMap;

    public AnomalyScoreLoader(InfoLoaderService infoLoaderService,
                              @Value("${anomaly.high.score:0}") int highScore,
                              @Value("${anomaly.mid.score:0}") int midScore,
                              @Value("${anomaly.low.score:0}") int lowScore) {
        this.infoLoaderService = infoLoaderService;
        this.levelScoreMap = Map.of("HIGH", highScore, "MID", midScore, "LOW", lowScore);
        log.info("INFO_LOAD | AnomalyScore LevelScore (from Environment): {}", levelScoreMap);
    }

    public void load() {
        long anomalyScoreVersion = infoLoaderService.getAnomalyScoreVersion();
        long patternVersion = infoLoaderService.getPatternVersion();
        long guardRailVersion = infoLoaderService.getGuardRailVersion();

        List<AnomalyScoreVO> scoreList = infoLoaderService.getAnomalyScore(anomalyScoreVersion);
        List<PatternInfo> patternList = infoLoaderService.getPatternInfo(patternVersion);
        List<GuardRailVO> guardRailList = infoLoaderService.getGuardRail(guardRailVersion);
        Map<String, String> levelMap = new ConcurrentHashMap<>();
        Set<String> enabledTargets = ConcurrentHashMap.newKeySet();

        addEnabledPatternTargets(enabledTargets, patternList);
        addEnabledGuardRailTargets(enabledTargets, guardRailList);

        for (AnomalyScoreVO vo : scoreList) {
            String key = compositeKey(vo.getMapperTable(), vo.getTargetId());
            levelMap.put(key, vo.getAnomalyLevelCd());
            log.info("INFO_LOAD | AnomalyScore: {} → {}", key, vo.getAnomalyLevelCd());
        }
        SCORE_LEVEL_REF.set(levelMap);
        ENABLED_TARGET_REF.set(enabledTargets);
        loadDocumentNames();

        log.info("INFO_LOAD | Rule Version : AnomalyScore:{} Pattern:{} GuardRail:{} | AnomalyScore Entries:{} | EnabledTargets:{} | LevelScore:{}",
                anomalyScoreVersion, patternVersion, guardRailVersion, levelMap.size(), enabledTargets.size(), levelScoreMap);
    }

    /**
     * (MAPR_TABLE, TARGET_ID) 조합으로 점수를 반환한다.
     *
     * @param mapperTable UI_GUARD_RAIL, UI_KEYWORD_CATEGORY, UI_PATTERN, UI_DOCUMENT_SIMILARITY
     * @param targetId    가드레일 카테고리, 키워드 SEQ, 패턴 코드, 유사 문서 ID
     * @return 점수 (설정이 없으면 0)
     */
    public int getScore(String mapperTable, String targetId) {
        String key = compositeKey(mapperTable, targetId);
        if (requiresUseYnCheck(mapperTable) && !ENABLED_TARGET_REF.get().contains(key)) return 0;

        String level = SCORE_LEVEL_REF.get().get(key);
        if (level == null) return 0;
        return levelScoreMap.getOrDefault(level, 0);
    }

    public int getImageCategoryScore(String imageCategorySeq) {
        if (imageCategorySeq == null) return 0;

        if (hasScore(TABLE_IMAGE_CATEGORY, imageCategorySeq)) {
            return getScore(TABLE_IMAGE_CATEGORY, imageCategorySeq);
        }
        return getScore(TABLE_KEYWORD_CATEGORY, imageCategorySeq);
    }

    public int getDocumentSimilarityScore(String documentId) {
        if (documentId == null) return 0;
        return getScore(TABLE_DOCUMENT_SIMILARITY, documentId);
    }

    public String getDocumentSimilarityName(String documentId) {
        if (documentId == null) return null;
        return DOCUMENT_NAME_REF.get().get(documentId.trim());
    }

    private void loadDocumentNames() {
        Map<String, String> documentNames = new ConcurrentHashMap<>();
        List<DocumentSimilarityVO> documents = infoLoaderService.getDocumentSimilarities();
        if (documents != null) {
            for (DocumentSimilarityVO document : documents) {
                if (document == null || document.getDocumentId() == null || document.getDocumentName() == null) continue;

                String documentId = document.getDocumentId().trim();
                String documentName = document.getDocumentName().trim();
                if (documentId.isEmpty() || documentName.isEmpty()) continue;
                documentNames.put(documentId, documentName);
            }
        }
        DOCUMENT_NAME_REF.set(documentNames);
        log.info("INFO_LOAD | DocumentSimilarity Names:{}", documentNames.size());
    }

    private boolean hasScore(String mapperTable, String targetId) {
        return SCORE_LEVEL_REF.get().containsKey(compositeKey(mapperTable, targetId));
    }

    private void addEnabledPatternTargets(Set<String> targetSet, List<PatternInfo> patterns) {
        if (patterns == null || patterns.isEmpty()) return;

        for (PatternInfo pattern : patterns) {
            if (pattern != null && pattern.getPatternCd() != null && "Y".equals(pattern.getUseYn())) {
                targetSet.add(compositeKey(TABLE_PATTERN, pattern.getPatternCd()));
            }
        }
    }

    private void addEnabledGuardRailTargets(Set<String> targetSet, List<GuardRailVO> guardRails) {
        if (guardRails == null || guardRails.isEmpty()) return;

        for (GuardRailVO guardRail : guardRails) {
            if (guardRail != null && guardRail.getGuardRailCd() != null && "Y".equals(guardRail.getUseYn())) {
                targetSet.add(compositeKey(TABLE_GUARD_RAIL, guardRail.getGuardRailCd()));
            }
        }
    }

    private boolean requiresUseYnCheck(String mapperTable) {
        return TABLE_PATTERN.equals(mapperTable) || TABLE_GUARD_RAIL.equals(mapperTable);
    }

    private String compositeKey(String mapperTable, String targetId) {
        return mapperTable + KEY_SEPARATOR + targetId;
    }
}
