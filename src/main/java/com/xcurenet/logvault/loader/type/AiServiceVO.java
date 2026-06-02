package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("AiServiceVO")
public class AiServiceVO {
    private String host;
    private Long categoryGroupSeq;
    private String categoryCd;
}
