package com.xcurenet.logvault.conf;

import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.loader.InfoLoader;
import com.xcurenet.logvault.module.task.service.TaskDispatcherService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
		infoLoader.init();
		taskDispatcherService.init();
	}

}
