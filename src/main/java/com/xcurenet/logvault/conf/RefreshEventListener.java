package com.xcurenet.logvault.conf;

import com.xcurenet.logvault.fs.FileProcessor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component("customRefreshEventListener")
public class RefreshEventListener implements ApplicationListener<RefreshScopeRefreshedEvent> {

	private final FileProcessor fileProcessor;

	@Override
	public void onApplicationEvent(@NotNull RefreshScopeRefreshedEvent event) {
		try {
			fileProcessor.init();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}