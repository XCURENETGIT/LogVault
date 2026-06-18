package com.xcurenet.logvault.loader.type;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockRuleJsonDto {

    private Long ruleVersion;
    private Boolean ruleIgnoreCase;
    private List<RuleEntry> rules;
    private List<CorpAccountEntry> corpAccount;
    private List<BlockMsgEntry> blockMsg;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleEntry {
        private Integer ruleSeq;
        private String ruleType;         // A:허용, B:차단
        private String ruleName;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Integer blockMsgSeq;
        private List<String> serviceCdList;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private List<IpRange> clientIpList;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private List<PortRange> clientPortList;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Conditions conditions;
        private String alarmYn;
        private String syslogYn;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String blockedLoggingYn;
        private String blockNonCorpAccountYn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpRange {
        private String startIp;
        private String endIp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortRange {
        private Integer startPort;
        private Integer endPort;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Conditions {
        private String target;           // A:프롬프트&첨부파일, F:첨부파일, P:프롬프트
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private List<KeywordEntry> keywordList;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private List<PatternEntry> patternList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeywordEntry {
        private String keyword;
        private Integer minCnt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatternEntry {
        private String pattern;
        private Integer minCnt;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private List<String> extensionList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockMsgEntry {
        private Integer blockMsgSeq;
        private String contentType;      // text/plain 또는 text/html
        private String blockMsg;         // Base64 인코딩된 content
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorpAccountEntry {
        private String serviceCd;
        private String matchType;
        private List<String> accounts;
    }

}
