package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.KeywordData;
import com.xcurenet.logvault.loader.type.KeywordInfo;
import com.xcurenet.logvault.loader.type.WorkDayInfo;
import com.xcurenet.logvault.module.util.WorkDayData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class KeywordLoader {

	private final InfoLoaderMapper mapper;
	private final AtomicReference<KeywordData> keywordData = new AtomicReference<>();
	private final KeywordInfo keywordInfo;

	public void load() {
		List<String> datas = mapper.getKeyword();
		for (String item : datas) {
			keywordInfo.addKeyword(CommonUtil.nvl(item));
		}
		log.info("[INFO_LOAD] Keyword Size: {}", datas.size());
		keywordInfo.prepare();
		keywordData.set(new KeywordData(keywordInfo));
	}
}
