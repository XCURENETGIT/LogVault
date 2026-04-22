package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.GuardRailVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@Getter
@Log4j2
@Service
@RequiredArgsConstructor
public class GuardRailLoader {

    private static final AtomicReference<Map<String, Integer>> GUARD_RAIL_MAP_REF = new AtomicReference<>();

    private final InfoLoaderService infoLoaderService;

    public void load() {
        List<GuardRailVO> datas = infoLoaderService.getGuardRail();
        log.info("INFO_LOAD | GuardRail Size: {}", datas.size());

        Map<String, Integer> guardRails = new LinkedHashMap<>();
        for (GuardRailVO item : datas) {
            log.debug("INFO_LOAD | GuardRail: {}", item);
            if (item == null || Common.isEquals(item.getUseYn(), "N")) continue;

            guardRails.put(item.getGuardRailCd(), item.getGuardRailOrder());
        }

        GUARD_RAIL_MAP_REF.set(guardRails);
    }

    public static int getGuardRailOrder(String code) {
        return GUARD_RAIL_MAP_REF.get().get(code);
    }

}
