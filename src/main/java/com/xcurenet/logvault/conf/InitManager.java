package com.xcurenet.logvault.conf;

import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.loader.InfoLoader;
import com.xcurenet.logvault.module.task.service.TaskDispatcherService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class InitManager {

	private final CryptoLoad cryptoLoad;
	private final OpenSearchInitializer openSearchInitializer;
	private final FileProcessor fileProcessor;
	private final InfoLoader infoLoader;
	private final TaskDispatcherService taskDispatcherService;

	@PostConstruct
	private void init() throws Exception {
		cryptoLoad.loadEncryptKey();
		openSearchInitializer.init();
		fileProcessor.init();
		try {
			infoLoader.init();
		} catch (Exception e) {
			log.error("Failed to initialize info loader", e);
		}
		taskDispatcherService.init();
	}

}
