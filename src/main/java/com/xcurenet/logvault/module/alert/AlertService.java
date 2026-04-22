package com.xcurenet.logvault.module.alert;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.exception.AlertException;
import com.xcurenet.logvault.loader.RuleLoader;
import com.xcurenet.logvault.loader.type.BlockRuleJsonDto;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.task.service.TaskMessageRepository;
import com.xcurenet.logvault.module.util.ActionType;
import com.xcurenet.logvault.module.util.RuleType;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class AlertService {
    private final RuleLoader ruleLoader;
    private final TaskMessageRepository repository;

    public void send(final ScanData data) {
        if (data == null || data.getEmassDoc() == null) {
            log.warn("{} | ScanData or EmassDoc is null", ErrorCode.ALERT_DOC_NULL.toString());
            return;
        }
        send(data.getEmassDoc());
    }

    public void send(final EmassDoc doc) {
        if (doc == null) {
            log.warn("{} | EmassDoc is null", ErrorCode.ALERT_DOC_NULL.toString());
            return;
        }

        StopWatch sw = DateUtils.start();
        try {
            if (!"S".equals(doc.getService().getSvc3())) return;

            BlockRuleJsonDto.RuleEntry matchedAllowRule = findMatchedAllowRule(doc);

            if (matchedAllowRule == null) return;

            log.info("ALT_RULE | {} | ruleSeq:{} | ruleName:{}", doc.getAction(), matchedAllowRule.getRuleSeq(), matchedAllowRule.getRuleName());

            AlertInfo alertInfo = findAlertInfo(doc, matchedAllowRule);

            AlertMessage message = new AlertMessage();
            message.setMsgId(doc.getMsgid());
            message.setData(JSONObject.toJSONString(alertInfo, JSONWriter.Feature.FieldBased));
            try {
                repository.insertAlertRule(message);
                log.info("ALT_SEND | KEYWORD_ALARM:{} | KEYWORD_SYSLOG:{} | PRIVACY_ALARM:{} | PRIVACY_SYSLOG:{} | {}", alertInfo.getKeywordAlarmTotal(), alertInfo.getKeywordSyslogTotal(), alertInfo.getPrivacyAlarmTotal(), alertInfo.getPrivacySyslogTotal(), DateUtils.stop(sw));
            } catch (Exception e) {
                log.error("{} | {}", ErrorCode.ALERT_REPOSITORY_FAIL.toString(), e.toString(), e);
            }
        } catch (Exception e) {
            log.error("{} | {}", ErrorCode.ALERT_INTERNAL_ERROR.toString(), e.toString(), e);
        }
    }

    private AlertInfo findAlertInfo(final EmassDoc doc, final BlockRuleJsonDto.RuleEntry rule) {
        try {
            AlertInfo result = new AlertInfo();
            result.setMsgid(doc.getMsgid());
            result.setAction(doc.getAction());
            result.setTimestamp(doc.getTimestamp().getTime());
            result.setCtime(doc.getCtime());
            result.setService(doc.getService());
            result.setUser(doc.getUser());

            result.setRuleSeq(rule.getRuleSeq());
            result.setRuleType(toRuleType(rule.getRuleType()));
            result.setRuleName(rule.getRuleName());

            Set<String> ruleKeywords = ruleKeywordSet(rule);
            Set<String> rulePatterns = rulePatternSet(rule);

            if (doc.getKeywordTotal() > 0) {
                if (Common.isEquals(rule.getAlarmYn(), "Y"))
                    result.setKeywordAlarm(filterKeywordInfo(doc.getKeywordInfo(), ruleKeywords));

                if (Common.isEquals(rule.getSyslogYn(), "Y"))
                    result.setKeywordSyslog(filterKeywordInfo(doc.getKeywordInfo(), ruleKeywords));
            }

            if (doc.getPrivacyTotal() > 0) {
                if (Common.isEquals(rule.getAlarmYn(), "Y"))
                    result.setPrivacyAlarm(filterPrivacy(doc.getPrivacyInfo(), rulePatterns));

                if (Common.isEquals(rule.getSyslogYn(), "Y"))
                    result.setPrivacySyslog(filterPrivacy(doc.getPrivacyInfo(), rulePatterns));
            }

            result.setKeywordAlarmTotal(result.getKeywordAlarm().getKeywords() == null ? 0 : result.getKeywordAlarm().getKeywords().size());
            result.setKeywordSyslogTotal(result.getKeywordSyslog().getKeywords() == null ? 0 : result.getKeywordSyslog().getKeywords().size());
            result.setPrivacyAlarmTotal(result.getPrivacyAlarm() == null ? 0 : result.getPrivacyAlarm().size());
            result.setPrivacySyslogTotal(result.getPrivacySyslog() == null ? 0 : result.getPrivacySyslog().size());

            return result;
        } catch (Exception e) {
            throw ExFactory.ex(AlertException::new, ErrorCode.ALERT_CALC_FAIL, java.util.Map.of("msgid", doc.getMsgid()));
        }
    }

    private List<EmassDoc.KeywordInfo.Keyword> filter(List<EmassDoc.KeywordInfo.Keyword> list, Set<String> loadKeywords) {
        if (list == null) return Collections.emptyList();
        return list.stream().filter(k -> loadKeywords.contains(k.getName())).toList();
    }

    /**
     * 현재 문서가 허용 룰(ruleType=A)에 해당되는지 확인한다.
     * 서비스, 클라이언트 IP/Port, 키워드/패턴 조건이 모두 일치하는 첫 번째 허용 룰을 반환한다.
     *
     * @return 매칭된 허용 룰. 없으면 null
     */
    private BlockRuleJsonDto.RuleEntry findMatchedAllowRule(final EmassDoc doc) {
        List<BlockRuleJsonDto.RuleEntry> rules = ruleLoader.getRules();
        if (rules == null || rules.isEmpty()) return null;

        for (BlockRuleJsonDto.RuleEntry rule : rules) {
            if (rule == null) continue;
//            if (!"A".equalsIgnoreCase(rule.getRuleType())) continue;
            if (!matchService(rule, doc)) continue;
            if (!matchClientIp(rule, doc)) continue;
            if (!matchClientPort(rule, doc)) continue;
            if (!matchConditions(rule, doc)) continue;
            return rule;
        }
        return null;
    }

    private boolean matchService(BlockRuleJsonDto.RuleEntry rule, EmassDoc doc) {
        List<String> serviceCdList = rule.getServiceCdList();
        if (serviceCdList == null || serviceCdList.isEmpty()) return true;
        if (doc.getService() == null) return false;
        String svc = doc.getService().getSvc();
        return svc != null && serviceCdList.contains(svc);
    }

    private boolean matchClientIp(BlockRuleJsonDto.RuleEntry rule, EmassDoc doc) {
        List<BlockRuleJsonDto.IpRange> clientIpList = rule.getClientIpList();
        if (clientIpList == null || clientIpList.isEmpty()) return true;
        String ip = getClientIp(doc);
        if (Common.isEmpty(ip)) return false;
        long ipLong = ipToLong(ip);
        if (ipLong < 0) return false;
        for (BlockRuleJsonDto.IpRange range : clientIpList) {
            if (range == null) continue;
            long start = ipToLong(range.getStartIp());
            long end = ipToLong(range.getEndIp());
            if (start < 0 || end < 0) continue;
            if (ipLong >= start && ipLong <= end) return true;
        }
        return false;
    }

    private boolean matchClientPort(BlockRuleJsonDto.RuleEntry rule, EmassDoc doc) {
        List<BlockRuleJsonDto.PortRange> clientPortList = rule.getClientPortList();
        if (clientPortList == null || clientPortList.isEmpty()) return true;
        int port = getClientPort(doc);
        if (port <= 0) return false;
        for (BlockRuleJsonDto.PortRange range : clientPortList) {
            if (range == null) continue;
            Integer start = range.getStartPort();
            Integer end = range.getEndPort();
            if (start == null || end == null) continue;
            if (port >= start && port <= end) return true;
        }
        return false;
    }

    /**
     * 파일 업로드(FU) 패턴 식별자.
     * PrivacyInfo 가 아닌 첨부파일 존재 여부 및 확장자로 매칭을 판단한다.
     */
    private static final String PATTERN_FILE_UPLOAD = "FU";

    private boolean matchConditions(BlockRuleJsonDto.RuleEntry rule, EmassDoc doc) {
        BlockRuleJsonDto.Conditions conditions = rule.getConditions();
        if (conditions == null) return true;

        boolean hasKeywordList = conditions.getKeywordList() != null && !conditions.getKeywordList().isEmpty();
        boolean hasPatternList = conditions.getPatternList() != null && !conditions.getPatternList().isEmpty();
        if (!hasKeywordList && !hasPatternList) return true;

        if (hasKeywordList && matchKeywordConditions(conditions.getKeywordList(), doc)) return true;

        return hasPatternList && matchPatternConditions(conditions.getPatternList(), doc);
    }

    /**
     * 룰의 키워드 리스트 중, 문서에서 탐지된 총 카운트가 minCnt 이상인 항목이 하나라도 있으면 true.
     * minCnt 가 null 이거나 1 미만이면 1 로 간주한다.
     */
    private boolean matchKeywordConditions(List<BlockRuleJsonDto.KeywordEntry> keywordList, EmassDoc doc) {
        Map<String, Integer> docKeywordCounts = collectKeywordCounts(doc);
        for (BlockRuleJsonDto.KeywordEntry entry : keywordList) {
            if (entry == null || entry.getKeyword() == null) continue;
            int minCnt = resolveMinCnt(entry.getMinCnt());
            int actualCnt = docKeywordCounts.getOrDefault(entry.getKeyword(), 0);
            if (actualCnt >= minCnt) return true;
        }
        return false;
    }

    /**
     * 룰의 패턴 리스트 중, 문서에서 탐지된 총 카운트가 minCnt 이상인 항목이 하나라도 있으면 true.
     * 단, 패턴이 "FU" 인 경우 파일 업로드로 간주하여 첨부파일 존재/확장자 기준으로 판단한다.
     */
    private boolean matchPatternConditions(List<BlockRuleJsonDto.PatternEntry> patternList, EmassDoc doc) {
        Map<String, Integer> docPatternCounts = collectPatternCounts(doc);
        for (BlockRuleJsonDto.PatternEntry entry : patternList) {
            if (entry == null || entry.getPattern() == null) continue;
            int minCnt = resolveMinCnt(entry.getMinCnt());
            if (PATTERN_FILE_UPLOAD.equalsIgnoreCase(entry.getPattern())) {
                if (matchFileUpload(doc, entry.getExtensionList())) return true;
                continue;
            }
            int actualCnt = docPatternCounts.getOrDefault(entry.getPattern(), 0);
            if (actualCnt >= minCnt) return true;
        }
        return false;
    }

    /**
     * 파일 업로드(FU) 매칭 처리.
     * - extensionList 가 비어있으면 첨부파일이 존재하는지 여부로 판단한다.
     * - extensionList 가 있으면 각 첨부의 extension 또는 expectedExtension 이
     * extensionList 에 포함된 경우에 true.
     * 확장자 비교는 대소문자를 구분하지 않는다.
     */
    private boolean matchFileUpload(EmassDoc doc, List<String> extensionList) {
        List<EmassDoc.Attach> attaches = doc.getAttach();
        if (attaches == null || attaches.isEmpty()) return false;

        boolean noFilter = extensionList == null || extensionList.isEmpty();
        Set<String> exts = noFilter ? Collections.emptySet() : extensionList.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        for (EmassDoc.Attach attach : attaches) {
            if (attach == null || !attach.isExist()) continue;
            if (noFilter || matchesExtension(attach, exts)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExtension(EmassDoc.Attach attach, Set<String> exts) {
        String ext = normalizeExtension(attach.getExtension());
        if (ext != null && exts.contains(ext)) return true;
        String expected = normalizeExtension(attach.getExpectedExtension());
        return expected != null && exts.contains(expected);
    }

    private String normalizeExtension(String ext) {
        if (Common.isEmpty(ext)) return null;
        return ext.toLowerCase(Locale.ROOT);
    }

    private int resolveMinCnt(Integer minCnt) {
        if (minCnt == null || minCnt < 1) return 1;
        return minCnt;
    }

    /**
     * 문서 내 탐지 키워드의 이름별 총 카운트 맵.
     * KeywordInfo.keywords 는 body/attach/attachName 이 병합된 결과이므로 이를 우선 사용하고,
     * null 인 경우에만 각 세부 리스트를 합산한다.
     */
    private Map<String, Integer> collectKeywordCounts(EmassDoc doc) {
        Map<String, Integer> counts = new HashMap<>();
        EmassDoc.KeywordInfo info = doc.getKeywordInfo();
        if (info == null) return counts;
        if (info.getKeywords() != null && !info.getKeywords().isEmpty()) {
            addKeywordCounts(counts, info.getKeywords());
        } else {
            addKeywordCounts(counts, info.getBody());
            addKeywordCounts(counts, info.getAttach());
            addKeywordCounts(counts, info.getAttachName());
        }
        return counts;
    }

    private void addKeywordCounts(Map<String, Integer> counts, List<EmassDoc.KeywordInfo.Keyword> list) {
        if (list == null) return;
        for (EmassDoc.KeywordInfo.Keyword k : list) {
            if (k == null || k.getName() == null) continue;
            counts.merge(k.getName(), k.getCount(), Integer::sum);
        }
    }

    /**
     * 문서 내 탐지 패턴(PrivacyInfo) 의 id 별 총 카운트 맵.
     * 동일 id 가 body/attach 등으로 복수 등장하면 카운트를 합산한다.
     */
    private Map<String, Integer> collectPatternCounts(EmassDoc doc) {
        Map<String, Integer> counts = new HashMap<>();
        if (doc.getPrivacyInfo() == null) return counts;
        for (EmassDoc.PrivacyInfo p : doc.getPrivacyInfo()) {
            if (p == null || p.getId() == null) continue;
            counts.merge(p.getId(), p.getCount(), Integer::sum);
        }
        return counts;
    }

    private String getClientIp(EmassDoc doc) {
        if (doc.getUser() != null && Common.isNotEmpty(doc.getUser().getIp())) return doc.getUser().getIp();
        return null;
    }

    private int getClientPort(EmassDoc doc) {
        if (doc.getUser() != null && doc.getUser().getProxyPort() > 0) return doc.getUser().getProxyPort();
        return 0;
    }

    private long ipToLong(String ip) {
        if (Common.isEmpty(ip)) return -1L;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return -1L;
        try {
            long result = 0L;
            for (String part : parts) {
                int v = Integer.parseInt(part);
                if (v < 0 || v > 255) return -1L;
                result = (result << 8) | v;
            }
            return result;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private RuleType toRuleType(String type) {
        if (type == null) return null;
        try {
            return RuleType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Set<String> ruleKeywordSet(BlockRuleJsonDto.RuleEntry rule) {
        if (rule.getConditions() == null || rule.getConditions().getKeywordList() == null) return Collections.emptySet();
        return rule.getConditions().getKeywordList().stream()
                .filter(k -> k != null && k.getKeyword() != null)
                .map(BlockRuleJsonDto.KeywordEntry::getKeyword)
                .collect(Collectors.toSet());
    }

    private Set<String> rulePatternSet(BlockRuleJsonDto.RuleEntry rule) {
        if (rule.getConditions() == null || rule.getConditions().getPatternList() == null) return Collections.emptySet();
        return rule.getConditions().getPatternList().stream()
                .filter(p -> p != null && p.getPattern() != null)
                .map(BlockRuleJsonDto.PatternEntry::getPattern)
                .collect(Collectors.toSet());
    }

    private EmassDoc.KeywordInfo filterKeywordInfo(EmassDoc.KeywordInfo src, Set<String> ruleKeywords) {
        if (src == null || ruleKeywords.isEmpty()) return null;

        EmassDoc.KeywordInfo r = new EmassDoc.KeywordInfo();
        r.setKeywords(filterByKeywordNames(src.getKeywords(), ruleKeywords));
        r.setBody(filterByKeywordNames(src.getBody(), ruleKeywords));
        r.setAttach(filterByKeywordNames(src.getAttach(), ruleKeywords));
        r.setAttachName(filterByKeywordNames(src.getAttachName(), ruleKeywords));
        r.setExist(!r.getKeywords().isEmpty());
        return r;
    }

    private List<EmassDoc.KeywordInfo.Keyword> filterByKeywordNames(List<EmassDoc.KeywordInfo.Keyword> list, Set<String> ruleKeywords) {
        if (list == null) return Collections.emptyList();
        return list.stream().filter(k -> k != null && ruleKeywords.contains(k.getName())).toList();
    }

    private List<EmassDoc.PrivacyInfo> filterPrivacy(List<EmassDoc.PrivacyInfo> list, Set<String> rulePatterns) {
        if (list == null || rulePatterns.isEmpty()) return null;

        return list.stream().filter(p -> p != null && rulePatterns.contains(p.getId())).toList();
    }

    @Data
    public static class AlertInfo {
        private String msgid;
        private ActionType action;
        private long timestamp;
        private String ctime;
        private EmassDoc.Service service;
        private EmassDoc.User user;

        private int ruleSeq;
        private RuleType ruleType;
        private String ruleName;

        private int keywordAlarmTotal;
        private int keywordSyslogTotal;
        private int privacyAlarmTotal;
        private int privacySyslogTotal;

        private EmassDoc.KeywordInfo keywordAlarm = new EmassDoc.KeywordInfo();
        private EmassDoc.KeywordInfo keywordSyslog = new EmassDoc.KeywordInfo();
        private List<EmassDoc.PrivacyInfo> privacyAlarm = new ArrayList<>();
        private List<EmassDoc.PrivacyInfo> privacySyslog = new ArrayList<>();
    }

}
