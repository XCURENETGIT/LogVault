package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.loader.AnomalyScoreLoader;
import com.xcurenet.logvault.loader.ImageCategoryLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class ReasonAnalysis {
    private static final String RULE_TARGET_ATTACH = "ATTACH";

    private final Config conf;
    private final AnomalyScoreLoader anomalyScoreLoader;
    private final ImageCategoryLoader imageCategoryLoader;

    /**
     * 샘플 메시지
     * [WMAIL]
     * CTIME : 2026/03/13 19:31:34
     * SOURCEIP : 1.225.49.111
     * DESTINATIONIP : 172.64.155.209
     * SOURCEPORT : 65379
     * DESTINATIONPORT : 443
     * HOST : chatgpt.com
     * URL : /backend-api/f/conversation
     * PROTOCOL : https
     * METHOD : POST
     * STYPE : IGP
     * ACTION : BLOCK
     * REASON :  보안 정책 안내\n\n⚠️ 내부 보안 정책에 의해 제한된 내용이 포함되어 답변이 중단되었습니다.\n 해당 내용을 제외한 후 다시 질문해 주세요.
     * DETECTIONS : 3;3;MDEqKioqKioqNjc=
     */
    /* =========================
     * 차단인 경우 사유가 들어온다.
     * id : 1~9 개인정보, 20~ 이상은 키워드
     * 개인정보 id 는 getId 메소드 참고
     * 1;2;abced==,3;2;abced==,5;2;abced==,5;2;abced==,5;2;abced==,5;2;abced==,5;2;abced==,2;2;abced==,8;3;abced==,6;2;abced==,9;3;abced==
     * id, confidence, detectStr(base64)
     * ========================= */
    public void setReason(final ScanData data) {
        MSGData msg = data.getMsgData();
        EmassDoc doc = data.getEmassDoc();
        appendImageSimilarityReason(doc, msg);

        if (msg.getDetections() == null) return;

        log.info("MGREASON | {}", msg.getDetections());

        List<String> items = Common.split(msg.getDetections(), ",");
        for (String item : items) {
            List<String> reason = Common.split(item, ";");
            if (reason.size() != 3) continue;

            int id = Common.nvz(reason.get(0));
            int confidence = Common.nvz(reason.get(1));
            String detectStr = Common.nvl(reason.get(2));
            boolean isAttach = Common.isEquals(Common.nvl(msg.getIsAttach()), "1");
            appendPrivacy(doc, id, confidence, detectStr, isAttach);
            appendKeyword(doc, id, confidence, detectStr, isAttach);
        }

        if (doc.getPrivacyInfo() != null && !doc.getPrivacyInfo().isEmpty()) {
            int total = doc.getPrivacyInfo().stream().mapToInt(EmassDoc.PrivacyInfo::getCount).sum();
            doc.setPrivacyTotal(total);
        }
        if (doc.getKeywordInfo() != null && doc.getKeywordInfo().getKeywords() != null && !doc.getKeywordInfo().getKeywords().isEmpty()) {
            doc.setKeywordTotal(doc.getKeywordInfo().getKeywords().size());
            int sum = doc.getKeywordInfo().getKeywords().stream().mapToInt(EmassDoc.KeywordInfo.Keyword::getCount).sum();
            doc.setKeywordTotal(sum);
        }
    }

    private void appendImageSimilarityReason(EmassDoc doc, MSGData msg) {
        if (doc == null || msg == null || !Common.isEquals(msg.getAction(), "BLOCK")) return;
        List<String> categoryIds = normalizeImageSimilarityCategoryIds(msg.getImageSimilarityCategoryIds());
        if (categoryIds.isEmpty()) return;

        doc.setRuleTarget(RULE_TARGET_ATTACH);

        EmassDoc.Attach attach = firstAttach(doc);
        if (attach == null) return;

        if (attach.getImageSimilarity() == null) {
            attach.setImageSimilarity(new ArrayList<>());
        }

        for (String categoryId : categoryIds) {
            String id = Common.nvl(categoryId).trim();
            if (Common.isEmpty(id)) continue;
            String seq = imageCategoryLoader.getCategorySeq(id);

            attach.getImageSimilarity().add(EmassDoc.ImageSimilarity.builder()
                    .categoryId(id)
                    .categoryName(imageCategoryLoader.getCategoryName(id))
                    .riskScore(anomalyScoreLoader.getImageCategoryScore(seq))
                    .build());
        }
    }

    private List<String> normalizeImageSimilarityCategoryIds(List<String> categoryIds) {
        List<String> result = new ArrayList<>();
        if (categoryIds == null || categoryIds.isEmpty()) return result;

        for (String categoryId : categoryIds) {
            String id = Common.nvl(categoryId).trim();
            if (Common.isEmpty(id) || Common.isEquals(id, "-")) continue;
            result.add(id);
        }
        return result;
    }

    private EmassDoc.Attach firstAttach(EmassDoc doc) {
        if (doc.getAttach() == null || doc.getAttach().isEmpty()) return null;
        for (EmassDoc.Attach attach : doc.getAttach()) {
            if (attach != null) return attach;
        }
        return null;
    }

    private void appendKeyword(EmassDoc doc, int id, int count, String detectStr, boolean isAttach) {
        if (id < 200000) return;

        String keyword = Common.decodeBase64ToString(detectStr);
        EmassDoc.KeywordInfo keywordInfo = doc.getKeywordInfo();
        if (keywordInfo == null) keywordInfo = new EmassDoc.KeywordInfo();

        List<EmassDoc.KeywordInfo.Keyword> keywords = isAttach ? keywordInfo.getAttach() : keywordInfo.getBody();
        if (keywords == null) keywords = new ArrayList<>();

        EmassDoc.KeywordInfo.Keyword exist = null;
        for (EmassDoc.KeywordInfo.Keyword k : keywords) {
            if (keyword.equals(k.getName()) && k.isBlocked()) {
                exist = k;
                break;
            }
        }

        if (exist == null) keywords.add(EmassDoc.KeywordInfo.Keyword.builder().name(keyword).count(count).blocked(true).build());
        else exist.setCount(exist.getCount() + count);

        keywordInfo.setExist(true);
        if (isAttach) {
            keywordInfo.setAttach(keywords);
        } else {
            keywordInfo.setBody(keywords);
        }
        keywordInfo.setKeywords(mergeKeywords(keywordInfo.getBody(), keywordInfo.getAttachName(), keywordInfo.getAttach()));

        doc.setKeywordInfo(keywordInfo);
    }

    @SafeVarargs
    private List<EmassDoc.KeywordInfo.Keyword> mergeKeywords(List<EmassDoc.KeywordInfo.Keyword>... sources) {
        Map<String, EmassDoc.KeywordInfo.Keyword> merged = new LinkedHashMap<>();
        for (List<EmassDoc.KeywordInfo.Keyword> source : sources) {
            if (source == null) continue;
            for (EmassDoc.KeywordInfo.Keyword keyword : source) {
                if (keyword == null || keyword.getName() == null) continue;
                String key = keyword.getName() + "\u0000" + keyword.isBlocked();
                EmassDoc.KeywordInfo.Keyword existing = merged.get(key);
                if (existing == null) {
                    merged.put(key, EmassDoc.KeywordInfo.Keyword.builder()
                            .name(keyword.getName())
                            .count(keyword.getCount())
                            .blocked(keyword.isBlocked())
                            .build());
                } else {
                    existing.setCount(existing.getCount() + keyword.getCount());
                }
            }
        }

        if (merged.isEmpty()) return null;
        return new ArrayList<>(merged.values());
    }

    private void appendPrivacy(EmassDoc doc, int id, int confidence, String detectStr, boolean isAttach) {
        if (id >= 200000) return;

        String encrypted = Common.encString(Common.decodeBase64ToString(detectStr).getBytes(StandardCharsets.UTF_8), conf.getEncryptKey(), conf.getEncyptCipher());
        String piId = getId(id);
        List<EmassDoc.PrivacyInfo> privacyInfos = doc.getPrivacyInfo();
        if (doc.getPrivacyInfo() == null) privacyInfos = new ArrayList<>();

        EmassDoc.PrivacyInfo info = getPrivacyInfo(privacyInfos, piId, true);
        if (info == null) {
            info = new EmassDoc.PrivacyInfo();
            info.setId(piId);
            info.setType(isAttach ? "A" : "B");
            info.setAttachName("-");
            info.setPrivacyData(new ArrayList<>(List.of(encrypted)));
            info.setCount(info.getPrivacyData().size());
            info.setBlocked(true);
            privacyInfos.add(info);
        } else {
            info.getPrivacyData().add(encrypted);
            info.setCount(info.getPrivacyData().size());
        }
        doc.setPrivacyInfo(privacyInfos);
    }

    private EmassDoc.PrivacyInfo getPrivacyInfo(List<EmassDoc.PrivacyInfo> privacyInfos, String piId, boolean blocked) {
        for (EmassDoc.PrivacyInfo info : privacyInfos) {
            if (info.getId().equals(piId) && info.isBlocked() == blocked) {
                return info;
            }
        }
        return null;
    }

    private String getId(int id) {
        return switch (id) {
            case 1 -> "SN";
            case 2 -> "CN";
            case 3 -> "MN";
            case 4 -> "EML";
            case 5 -> "BN";
            case 6 -> "PN";
            case 7 -> "DN";
            case 8 -> "SSN";
            case 9 -> "AN";
            case 10 -> "BRN";
            case 11 -> "FN";
            case 12 -> "VN_CCCD";
            case 13 -> "VN_MN";
            case 14 -> "VN_PN";
            case 15 -> "VN_TIN";
            case 16 -> "VN_SI";
            default -> "-";
        };
    }
}
