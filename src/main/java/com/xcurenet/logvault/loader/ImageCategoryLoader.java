package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.ImageCategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class ImageCategoryLoader {

    private final InfoLoaderService infoLoaderService;
    private final AtomicReference<Map<String, String>> IMAGE_CATEGORY_SEQ_REF = new AtomicReference<>(Collections.emptyMap());
    private final AtomicReference<Map<String, String>> IMAGE_CATEGORY_NAME_REF = new AtomicReference<>(Collections.emptyMap());

    public void load() {
        List<ImageCategoryVO> categories = infoLoaderService.getImageCategories();
        Map<String, String> categorySeqMap = new ConcurrentHashMap<>();
        Map<String, String> categoryNameMap = new ConcurrentHashMap<>();

        for (ImageCategoryVO item : categories) {
            log.debug("INFO_LOAD | ImageCategory: {}", item);
            if (item == null || Common.isEmpty(item.getImageCategoryId()) || Common.isEmpty(item.getImageCategorySeq())) continue;

            String id = normalizeId(item.getImageCategoryId());
            categorySeqMap.put(id, item.getImageCategorySeq().trim());
            if (Common.isNotEmpty(item.getImageCategoryNm())) {
                categoryNameMap.put(id, item.getImageCategoryNm());
            }
        }

        IMAGE_CATEGORY_SEQ_REF.set(categorySeqMap);
        IMAGE_CATEGORY_NAME_REF.set(categoryNameMap);
        log.info("INFO_LOAD | ImageCategory Size: {}", categorySeqMap.size());
    }

    public String getCategorySeq(String imageCategoryId) {
        if (Common.isEmpty(imageCategoryId)) return null;
        return IMAGE_CATEGORY_SEQ_REF.get().get(normalizeId(imageCategoryId));
    }

    public String getCategoryName(String imageCategoryId) {
        if (Common.isEmpty(imageCategoryId)) return null;
        return IMAGE_CATEGORY_NAME_REF.get().get(normalizeId(imageCategoryId));
    }

    private String normalizeId(String imageCategoryId) {
        return imageCategoryId.trim().toLowerCase(Locale.ROOT);
    }
}
