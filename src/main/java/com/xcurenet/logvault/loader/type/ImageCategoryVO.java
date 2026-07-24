package com.xcurenet.logvault.loader.type;

import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("ImageCategoryVO")
public class ImageCategoryVO {
    private String imageCategorySeq;
    private String imageCategoryId;
}
