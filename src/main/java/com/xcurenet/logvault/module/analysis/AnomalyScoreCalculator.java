package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.AnomalyScoreLoader;
import com.xcurenet.logvault.loader.KeywordLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.util.ActionType;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * EmassDoc 의 분석 결과를 기반으로 이상행위 점수를 계산한다.
 * 본문과 각 첨부파일의 탐지 결과를 개별로 합산하여 전체 문서 점수를 산출한다.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class AnomalyScoreCalculator {

    private static final String PATTERN_CODE_EXIST = "SC";       // 소스코드 포함
    private static final String PATTERN_FILE_UPLOAD = "FU";      // 파일업로드
    private static final String PATTERN_SIMILARITY = "CF";       // 기밀문서(대외비) 유사도

    private static final String PATTERN_PERSONAL_ACCOUNT = "PA";
    private static final String PATTERN_WORK = "WRK";
    private static final String RULE_TARGET_ACCOUNT = "ACCOUNT";
    private static final int ML_CATEGORY_NOT_WORK = 2;

    private final AnomalyScoreLoader anomalyScoreLoader;
    private final KeywordLoader keywordLoader;

    /**
     * ScanData 기반 점수 계산 (분석 파이프라인에서 호출)
     */
    public void calculate(final ScanData data) {
        if (data == null || data.getEmassDoc() == null) return;
        calculate(data.getEmassDoc());
    }

    /**
     * EmassDoc 기반 점수 계산 (ML 후처리 등에서 직접 호출)
     */
    public void calculate(final EmassDoc doc) {
        if (doc == null) return;

        EmassDoc.AnomalyScore score = new EmassDoc.AnomalyScore();

        calcGuardrail(doc, score.getGuardrail());
        calcKeyword(doc, score.getKeyword());
        calcPattern(doc, score.getPattern(), score.getAttach(), score.getCodeExist(), score.getSimilarity(), score.getAccount(), score.getWork());
//        calcImageSimilarity(doc, score.getImageSimilarity());

        log.debug("ANOMALY_SCORE | msgid={} | guardrail={}({}) keyword={}({}) pattern={}({}) code_exist={}({}) similarity={}({}) image_similarity={}({}) account={}({}) work={}({})",
                doc.getMsgid(),
                score.getGuardrail().getScore(), score.getGuardrail().getCount(),
                score.getKeyword().getScore(), score.getKeyword().getCount(),
                score.getPattern().getScore(), score.getPattern().getCount(),
                score.getCodeExist().getScore(), score.getCodeExist().getCount(),
                score.getSimilarity().getScore(), score.getSimilarity().getCount(),
                score.getImageSimilarity().getScore(), score.getImageSimilarity().getCount(),
                score.getAccount().getScore(), score.getAccount().getCount(),
                score.getWork().getScore(), score.getWork().getCount());

        score.calculateTotal();

        log.debug("ANOMALY_SCORE | msgid={} | total={}({})",
                doc.getMsgid(), score.getTotal().getScore(), score.getTotal().getCount());

        doc.setAnomalyScore(score);
    }


    // ──────────────────────────────────────────────
    //  Guardrail 점수: 본문 + 각 첨부의 guardrail_category
    // ──────────────────────────────────────────────

    private void calcGuardrail(EmassDoc doc, EmassDoc.AnomalyScore.ScoreEntry entry) {
        // 본문
        if (doc.getBody() != null) {
            addGuardrailScore(entry, doc.getBody().getGuardrailCategory());
        }

        // 각 첨부
        if (doc.getAttach() != null) {
            for (EmassDoc.Attach attach : doc.getAttach()) {
                addGuardrailScore(entry, attach.getGuardrailCategory());
            }
        }
    }

    private void addGuardrailScore(EmassDoc.AnomalyScore.ScoreEntry entry, String category) {
        if (Common.isEmpty(category) || "SAFE".equalsIgnoreCase(category)) return;
        entry.add(anomalyScoreLoader.getScore(AnomalyScoreLoader.TABLE_GUARD_RAIL, category));
    }


    // ──────────────────────────────────────────────
    //  Keyword 점수: body + attach + attach_name 키워드 각각 합산
    // ──────────────────────────────────────────────

    private void calcKeyword(EmassDoc doc, EmassDoc.AnomalyScore.ScoreEntry entry) {
        EmassDoc.KeywordInfo ki = doc.getKeywordInfo();
        if (ki == null || !ki.isExist()) return;

        sumKeywordList(entry, ki.getKeywords(), isBlocked(doc));
    }

    private void sumKeywordList(EmassDoc.AnomalyScore.ScoreEntry entry, List<EmassDoc.KeywordInfo.Keyword> keywords, boolean blockedOnly) {
        if (keywords == null || keywords.isEmpty()) return;

        for (EmassDoc.KeywordInfo.Keyword kw : keywords) {
            if (kw == null || Common.isEmpty(kw.getName()) || (blockedOnly && !kw.isBlocked())) continue;
            String categorySeq = keywordLoader.getCategorySeq(kw.getName());
            if (categorySeq == null) continue;

            for (int i = 0; i < kw.getCount(); i++) {
                entry.add(anomalyScoreLoader.getScore(AnomalyScoreLoader.TABLE_KEYWORD_CATEGORY, categorySeq));
            }
        }
    }


    // ──────────────────────────────────────────────
    //  Pattern 점수: 개인정보 패턴 + ML 패턴 (본문·첨부 각각)
    // ──────────────────────────────────────────────

    private void calcPattern(EmassDoc doc, EmassDoc.AnomalyScore.ScoreEntry patternEntry,
                             EmassDoc.AnomalyScore.ScoreEntry attachEntry,
                             EmassDoc.AnomalyScore.ScoreEntry codeExistEntry,
                             EmassDoc.AnomalyScore.ScoreEntry similarityEntry,
                             EmassDoc.AnomalyScore.ScoreEntry accountEntry,
                             EmassDoc.AnomalyScore.ScoreEntry workEntry) {
        // 1. 개인정보 패턴 (privacy_info): 각 탐지 항목별 점수
        calcPrivacyPattern(doc, patternEntry);

        calcFileUploadPattern(doc, attachEntry);

        calcAccountPattern(doc, accountEntry);

        // 2. ML 패턴: 본문 ml_result
        if (doc.getBody() != null) {
            calcMlPattern(codeExistEntry, similarityEntry, workEntry, doc.getBody().getMlResult());
        }

        // 3. ML 패턴: 각 첨부 ml_result
        if (doc.getAttach() != null) {
            for (EmassDoc.Attach attach : doc.getAttach()) {
                calcMlPattern(codeExistEntry, similarityEntry, workEntry, attach.getMlResult());
            }
        }
    }

    /**
     * 개인정보 패턴 점수: privacy_info 각 항목의 id(=PATTERN_CD)로 점수 합산.
     * 같은 패턴이 본문·첨부에 각각 탐지되면 각각 별도 점수.
     */
    private void calcPrivacyPattern(EmassDoc doc, EmassDoc.AnomalyScore.ScoreEntry entry) {
        List<EmassDoc.PrivacyInfo> privacyInfos = doc.getPrivacyInfo();
        if (privacyInfos == null || privacyInfos.isEmpty()) return;

        boolean blockedOnly = isBlocked(doc);
        for (EmassDoc.PrivacyInfo info : privacyInfos) {
            if (info == null || Common.isEmpty(info.getId()) || (blockedOnly && !info.isBlocked())) continue;
            for (int i = 0; i < info.getCount(); i++) {
                entry.add(anomalyScoreLoader.getScore(AnomalyScoreLoader.TABLE_PATTERN, info.getId()));
            }
        }
    }

    private void calcFileUploadPattern(EmassDoc doc, EmassDoc.AnomalyScore.ScoreEntry entry) {
        int attachExistCount = doc.getAttachExistCount();
        if (attachExistCount <= 0) return;
        if (isBlocked(doc) && !isFileUploadBlockReason(doc)) return;

        int fileUploadScore = anomalyScoreLoader.getScore(AnomalyScoreLoader.TABLE_PATTERN, PATTERN_FILE_UPLOAD);

        for (int i = 0; i < attachExistCount; i++) {
            entry.add(fileUploadScore);
        }
    }

//    private void calcImageSimilarity(EmassDoc doc, EmassDoc.AnomalyScore.ScoreEntry entry) {
//        if (doc.getAttach() == null || doc.getAttach().isEmpty()) return;
//
//        for (EmassDoc.Attach attach : doc.getAttach()) {
//            addImageSimilarityScore(entry, attach.getImageSimilarity());
//        }
//    }
//
//    private void addImageSimilarityScore(EmassDoc.AnomalyScore.ScoreEntry entry, List<EmassDoc.ImageSimilarity> imageSimilarities) {
//        if (imageSimilarities == null || imageSimilarities.isEmpty()) return;
//
//        for (EmassDoc.ImageSimilarity imageSimilarity : imageSimilarities) {
//            if (imageSimilarity == null || Common.isEmpty(imageSimilarity.getCategoryId()) || imageSimilarity.getRiskScore() == null) continue;
//            entry.add(imageSimilarity.getRiskScore());
//        }
//    }

    private void calcAccountPattern(EmassDoc doc, EmassDoc.AnomalyScore.ScoreEntry entry) {
        if (doc.getUser() == null || doc.getUser().getCompanyAccount() == null || doc.getUser().getCompanyAccount()) return;
        if (isBlocked(doc) && !isAccountBlockReason(doc)) return;

        entry.add(anomalyScoreLoader.getScore(AnomalyScoreLoader.TABLE_PATTERN, PATTERN_PERSONAL_ACCOUNT));
    }

    private boolean isFileUploadBlockReason(EmassDoc doc) {
        return isBlocked(doc) && Common.isNotEmpty(doc.getBlockExtension());
    }

    private boolean isAccountBlockReason(EmassDoc doc) {
        return isBlocked(doc) && Common.isEquals(doc.getRuleTarget(), RULE_TARGET_ACCOUNT);
    }

    private boolean isBlocked(EmassDoc doc) {
        return doc != null && doc.getAction() == ActionType.BLOCK;
    }

    /**
     * ML 패턴 점수: ml_result 조건에 따라 PATTERN_CD(SC, CF, WRK) 점수 부여.
     * 각 본문/첨부의 ml_result 별로 독립 계산.
     */
    private void calcMlPattern(EmassDoc.AnomalyScore.ScoreEntry codeExistEntry,
                               EmassDoc.AnomalyScore.ScoreEntry similarityEntry,
                               EmassDoc.AnomalyScore.ScoreEntry workEntry,
                               EmassDoc.MLResult mlResult) {
        if (mlResult == null) return;

        // SC: 소스코드 포함
        if (mlResult.isCodeExist()) {
            codeExistEntry.add(anomalyScoreLoader.getScore(AnomalyScoreLoader.TABLE_PATTERN, PATTERN_CODE_EXIST));
        }

        // CF: 기밀문서 유사도 탐지
        if (mlResult.isSimilarityExist()) {
            similarityEntry.add(anomalyScoreLoader.getScore(AnomalyScoreLoader.TABLE_PATTERN, PATTERN_SIMILARITY));
        }

        if (mlResult.getCategory() == ML_CATEGORY_NOT_WORK) {
            workEntry.add(anomalyScoreLoader.getScore(AnomalyScoreLoader.TABLE_PATTERN, PATTERN_WORK));
        }

    }
}
