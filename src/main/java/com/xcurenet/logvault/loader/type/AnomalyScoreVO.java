package com.xcurenet.logvault.loader.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("AnomalyScoreVO")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnomalyScoreVO {
    @JsonProperty("mapperTable")
    private String mapperTable;

    @JsonProperty("targetId")
    private String targetId;

    @JsonProperty("anomalyLevelCd")
    private String anomalyLevelCd;
}
