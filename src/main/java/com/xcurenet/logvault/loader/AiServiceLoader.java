package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.AiServiceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class AiServiceLoader {

    private final InfoLoaderService infoLoaderService;
    private final AtomicReference<Map<String, AiServiceVO>> AI_SERVICE_REF = new AtomicReference<>(Collections.emptyMap());

    public void load() {
        List<AiServiceVO> aiServices = infoLoaderService.getAiServices();
        Map<String, AiServiceVO> aiServiceMap = new LinkedHashMap<>();

        for (AiServiceVO item : aiServices) {
            log.debug("INFO_LOAD | AiService: {}", item);
            if (item == null || Common.isEmpty(item.getHost())) continue;

            aiServiceMap.put(normalizeHost(item.getHost()), item);
        }

        AI_SERVICE_REF.set(Collections.unmodifiableMap(aiServiceMap));
        log.info("INFO_LOAD | AiService Size: {}", aiServiceMap.size());
    }

    public AiServiceVO get(String host) {
        if (Common.isEmpty(host)) return null;
        return AI_SERVICE_REF.get().get(normalizeHost(host));
    }

    public boolean contains(String host) {
        return get(host) != null;
    }

    public Map<String, AiServiceVO> getAll() {
        return AI_SERVICE_REF.get();
    }

    private String normalizeHost(String host) {
        return host.trim().toLowerCase(Locale.ROOT);
    }
}
