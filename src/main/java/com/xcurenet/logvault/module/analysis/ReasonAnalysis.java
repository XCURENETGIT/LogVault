package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
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
    private final Config conf;

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
        if (msg.getDetections() == null) return;

        log.info("MGREASON | {}", msg.getDetections());

        EmassDoc doc = data.getEmassDoc();
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
        if (doc.getKeywordInfo() != null && !doc.getKeywordInfo().getKeywords().isEmpty()) {
            doc.setKeywordTotal(doc.getKeywordInfo().getKeywords().size());
            int sum = doc.getKeywordInfo().getKeywords().stream().mapToInt(EmassDoc.KeywordInfo.Keyword::getCount).sum();
            doc.setKeywordTotal(sum);
        }
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
            if (keyword.equals(k.getName())) {
                exist = k;
                break;
            }
        }

        if (exist == null) keywords.add(EmassDoc.KeywordInfo.Keyword.builder().name(keyword).count(count).build());
        else exist.setCount(exist.getCount() + 1);

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
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (List<EmassDoc.KeywordInfo.Keyword> source : sources) {
            if (source == null) continue;
            for (EmassDoc.KeywordInfo.Keyword keyword : source) {
                if (keyword == null || keyword.getName() == null) continue;
                merged.merge(keyword.getName(), keyword.getCount(), Integer::sum);
            }
        }

        if (merged.isEmpty()) return null;

        List<EmassDoc.KeywordInfo.Keyword> keywords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            keywords.add(EmassDoc.KeywordInfo.Keyword.builder().name(entry.getKey()).count(entry.getValue()).build());
        }
        return keywords;
    }

    private void appendPrivacy(EmassDoc doc, int id, int confidence, String detectStr, boolean isAttach) {
        if (id > 200000) return;

        String encrypted = Common.encString(Common.decodeBase64ToString(detectStr).getBytes(StandardCharsets.UTF_8), conf.getEncryptKey(), conf.getEncyptCipher());
        String piId = getId(id);
        List<EmassDoc.PrivacyInfo> privacyInfos = doc.getPrivacyInfo();
        if (doc.getPrivacyInfo() == null) privacyInfos = new ArrayList<>();

        EmassDoc.PrivacyInfo info = getPrivacyInfo(privacyInfos, piId);
        if (info == null) {
            info = new EmassDoc.PrivacyInfo();
            info.setId(piId);
            info.setType(isAttach ? "A" : "B");
            info.setAttachName("-");
            info.setPrivacyData(new ArrayList<>(List.of(encrypted)));
            info.setCount(info.getPrivacyData().size());
            privacyInfos.add(info);
        } else {
            info.getPrivacyData().add(encrypted);
            info.setCount(info.getPrivacyData().size());
        }
        doc.setPrivacyInfo(privacyInfos);
    }

    private EmassDoc.PrivacyInfo getPrivacyInfo(List<EmassDoc.PrivacyInfo> privacyInfos, String piId) {
        for (EmassDoc.PrivacyInfo info : privacyInfos) {
            if (info.getId().equals(piId)) {
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
            default -> "-";
        };
    }
}
