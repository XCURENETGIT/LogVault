package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.utils.LangDetectUtil;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class BodyLanguage {
	private final Config conf;
	private final LangDetectUtil langDetectUtil;

	public void detect(final ScanData msg) {
		try {
			EmassDoc doc = msg.getEmassDoc();
			EmassDoc.Body body = doc.getBody();
			if (body != null && body.getText() != null) {
				int maxLen = Math.min(conf.getBodyLanguageDetectSize(), body.getText().length());
				body.setLanguage(langDetectUtil.detectLanguage(body.getText().substring(0, maxLen)));
				log.debug("BODY_LANG | {}", body.getLanguage());
			}
		} catch (Exception e) {
			log.warn("BODY_LANG | {}", e.getMessage());
		}
	}
}
