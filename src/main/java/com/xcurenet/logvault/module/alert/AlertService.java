package com.xcurenet.logvault.module.alert;

import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.loader.KeywordLoader;
import com.xcurenet.logvault.loader.PatternLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.task.service.TaskMessageRepository;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class AlertService {
	private final KeywordLoader keywordLoader;
	private final PatternLoader patternLoader;
	private final TaskMessageRepository repository;

	public void send(final ScanData data) {
		send(data.getEmassDoc());
	}

	public void send(final EmassDoc doc) {
		StopWatch sw = DateUtils.start();
		try {
			if (!doc.getService().getSvc3().equals("S")) return; // 발신 서비스만 처리

			AlertInfo alertInfo = findAlertInfo(doc);
			if (alertInfo.getKeywordAlarmTotal() + alertInfo.getKeywordSyslogTotal() + alertInfo.getPrivacyAlarmTotal() + alertInfo.getPrivacySyslogTotal() > 0) {
				AlertMessage message = new AlertMessage();
				message.setMsgId(doc.getMsgid());
				message.setData(JSONObject.toJSONString(alertInfo));

				repository.insertAlertRule(message);

				log.info("ALT_SEND | keyword_alarm:{} | keyword_syslog:{} | privacy_alarm:{} | privacy_syslog:{} | {}", alertInfo.getKeywordAlarmTotal(), alertInfo.getKeywordSyslogTotal(), alertInfo.getPrivacyAlarmTotal(), alertInfo.getPrivacySyslogTotal(), DateUtils.stop(sw));
			}
		} catch (Exception e) {
			log.warn("ALT_SEND | {}", e.getMessage());
			log.error("", e);
		}
	}

	private AlertInfo findAlertInfo(EmassDoc doc) {
		AlertInfo result = new AlertInfo();
		result.setMsgid(doc.getMsgid());
		result.setTimestamp(doc.getTimestamp().getTime());
		result.setCtime(doc.getCtime());
		result.setService(doc.getService());
		result.setUser(doc.getUser());

		if (doc.getKeywordTotal() > 0) { // 키워드 탐지
			result.setKeywordAlarm(findKeywords(doc.getKeywordInfo(), keywordLoader.getKeywordAlert()));
			result.setKeywordSyslog(findKeywords(doc.getKeywordInfo(), keywordLoader.getKeywordSyslog()));
			result.setKeywordAlarmTotal(result.getKeywordAlarm().getKeywords().size());
			result.setKeywordSyslogTotal(result.getKeywordSyslog().getKeywords().size());
		}
		if (doc.getPrivacyTotal() > 0) { // 개인정보 탐지
			result.setPrivacyAlarm(findPrivacy(doc.getPrivacyInfo(), patternLoader.getPatternAlert()));
			result.setPrivacySyslog(findPrivacy(doc.getPrivacyInfo(), patternLoader.getPatternSyslog()));
			result.setPrivacyAlarmTotal(result.getPrivacyAlarm().size());
			result.setPrivacySyslogTotal(result.getPrivacySyslog().size());
		}
		return result;
	}

	private EmassDoc.KeywordInfo findKeywords(EmassDoc.KeywordInfo src, Set<String> loadKeywords) {
		EmassDoc.KeywordInfo r = new EmassDoc.KeywordInfo();
		r.setKeywords(filter(src.getKeywords(), loadKeywords));
		r.setBody(filter(src.getBody(), loadKeywords));
		r.setAttach(filter(src.getAttach(), loadKeywords));
		r.setAttachName(filter(src.getAttachName(), loadKeywords));
		r.setExist(!r.getKeywords().isEmpty());
		return r;
	}

	private List<EmassDoc.KeywordInfo.Keyword> filter(List<EmassDoc.KeywordInfo.Keyword> list, Set<String> loadKeywords) {
		if (list == null) return Collections.emptyList();
		return list.stream().filter(k -> loadKeywords.contains(k.getName())).toList();
	}

	private List<EmassDoc.PrivacyInfo> findPrivacy(List<EmassDoc.PrivacyInfo> privacyInfos, Set<String> loadPatterns) {
		if (privacyInfos == null) return null;
		return privacyInfos.stream().filter(k -> loadPatterns.contains(k.getId())).toList();
	}

	@Data
	public static class AlertInfo {
		private String msgid;
		private long timestamp;
		private String ctime;
		private EmassDoc.Service service;
		private EmassDoc.User user;

		private int keywordAlarmTotal;
		private int keywordSyslogTotal;
		private int privacyAlarmTotal;
		private int privacySyslogTotal;

		private EmassDoc.KeywordInfo keywordAlarm = new EmassDoc.KeywordInfo();
		private EmassDoc.KeywordInfo keywordSyslog = new EmassDoc.KeywordInfo();
		private List<EmassDoc.PrivacyInfo> privacyAlarm = new ArrayList<>();
		private List<EmassDoc.PrivacyInfo> privacySyslog = new ArrayList<>();
	}
}
