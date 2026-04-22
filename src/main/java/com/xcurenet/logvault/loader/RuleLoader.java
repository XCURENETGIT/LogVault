package com.xcurenet.logvault.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.loader.type.BlockRuleJsonDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class RuleLoader {

	private final Config conf;
	private final FileProcessor fileProcessor;
	private final ObjectMapper objectMapper;

	private final AtomicReference<BlockRuleJsonDto> RULE_REF = new AtomicReference<>();

	public void load() {
		String path = Common.makeFilepath(conf.getDecryptFilePath(), conf.getDecryptFileRuleJson());
		try {
			if (!fileProcessor.exists(path)) {
				log.warn("INFO_LOAD | Rule file not found: {}", path);
				RULE_REF.set(emptyRule());
				return;
			}
			try (InputStream is = fileProcessor.open(path)) {
				BlockRuleJsonDto dto = objectMapper.readValue(is, BlockRuleJsonDto.class);
                dto.setBlockMsg(null);
				RULE_REF.set(dto);
				int ruleSize = dto.getRules() == null ? 0 : dto.getRules().size();
				log.info("INFO_LOAD | Rule Version : {} | Rule Size: {}", dto.getRuleVersion(), ruleSize);
			}
		} catch (Exception e) {
			log.error("INFO_LOAD | Failed to load rule file: {} | {}", path, e.toString(), e);
			if (RULE_REF.get() == null) {
				RULE_REF.set(emptyRule());
			}
		}
	}

    public List<BlockRuleJsonDto.RuleEntry> getRules() {
		BlockRuleJsonDto dto = RULE_REF.get();
		if (dto == null || dto.getRules() == null) return Collections.emptyList();
		return dto.getRules();
	}

	private BlockRuleJsonDto emptyRule() {
		return new BlockRuleJsonDto(0L, Collections.emptyList(), Collections.emptyList());
	}
}
