package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("DocumentSimilarityVO")
public class DocumentSimilarityVO {
    private String documentId;
    private String documentName;
}
