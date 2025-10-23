package com.xcurenet.logvault.loader;

import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.ConfVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConfigLoader {
	private final InfoLoaderMapper mapper;
	public final AtomicReference<ConfVO> CONFIG = new AtomicReference<>();

	public void load() {
		List<ConfVO> keywords = mapper.getUIConf();
		log.info("[INFO_LOAD] Keyword Size: {}", keywords.size());
	}
}
