package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("AnomalyScoreVO")
public class AnomalyScoreVO {
    private String mapperTable;
    private String targetId;
    private String anomalyLevelCd;
}
