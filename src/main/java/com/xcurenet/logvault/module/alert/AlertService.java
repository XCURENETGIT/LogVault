package com.xcurenet.logvault.module.alert;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.exception.AlertException;
import com.xcurenet.logvault.loader.KeywordLoader;
import com.xcurenet.logvault.loader.PatternLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.task.service.TaskMessageRepository;
import com.xcurenet.logvault.module.util.ActionType;
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
		if (data == null || data.getEmassDoc() == null) {
			log.warn("{} | ScanData or EmassDoc is null", ErrorCode.ALERT_DOC_NULL.toString());
			return;
		}
		send(data.getEmassDoc());
	}

	public void send(final EmassDoc doc) {
		if (doc == null) {
			log.warn("{} | EmassDoc is null", ErrorCode.ALERT_DOC_NULL.toString());
			return;
		}

		StopWatch sw = DateUtils.start();
		try {
			if (!"S".equals(doc.getService().getSvc3())) return;

			AlertInfo alertInfo = findAlertInfo(doc);
			int total = alertInfo.getKeywordAlarmTotal() + alertInfo.getKeywordSyslogTotal() + alertInfo.getPrivacyAlarmTotal() + alertInfo.getPrivacySyslogTotal();
			if (total <= 0) return;

			AlertMessage message = new AlertMessage();
			message.setMsgId(doc.getMsgid());
			message.setData(JSONObject.toJSONString(alertInfo, JSONWriter.Feature.FieldBased));
			log.info(JSONObject.toJSONString(alertInfo, JSONWriter.Feature.FieldBased));
			try {
				repository.insertAlertRule(message);
				log.info("ALT_SEND | KEYWORD_ALARM:{} | KEYWORD_SYSLOG:{} | PRIVACY_ALARM:{} | PRIVACY_SYSLOG:{} | {}", alertInfo.getKeywordAlarmTotal(), alertInfo.getKeywordSyslogTotal(), alertInfo.getPrivacyAlarmTotal(), alertInfo.getPrivacySyslogTotal(), DateUtils.stop(sw));
			} catch (Exception e) {
				log.error("{} | {}", ErrorCode.ALERT_REPOSITORY_FAIL.toString(), e.toString(), e);
			}
		} catch (Exception e) {
			log.error("{} | {}", ErrorCode.ALERT_INTERNAL_ERROR.toString(), e.toString(), e);
		}
	}

	private AlertInfo findAlertInfo(final EmassDoc doc) {
		try {
			AlertInfo result = new AlertInfo();
			result.setMsgid(doc.getMsgid());
			result.setAction(doc.getAction());
			result.setTimestamp(doc.getTimestamp().getTime());
			result.setCtime(doc.getCtime());
			result.setService(doc.getService());
			result.setUser(doc.getUser());

			if (doc.getKeywordTotal() > 0) {
				result.setKeywordAlarm(findKeywords(doc.getKeywordInfo(), keywordLoader.getKeywordAlert()));
				result.setKeywordSyslog(findKeywords(doc.getKeywordInfo(), keywordLoader.getKeywordSyslog()));
				result.setKeywordAlarmTotal(result.getKeywordAlarm().getKeywords().size());
				result.setKeywordSyslogTotal(result.getKeywordSyslog().getKeywords().size());
			}

			if (doc.getPrivacyTotal() > 0) {
				result.setPrivacyAlarm(findPrivacy(doc.getPrivacyInfo(), patternLoader.getPatternAlert()));
				result.setPrivacySyslog(findPrivacy(doc.getPrivacyInfo(), patternLoader.getPatternSyslog()));
				result.setPrivacyAlarmTotal(result.getPrivacyAlarm().size());
				result.setPrivacySyslogTotal(result.getPrivacySyslog().size());
			}
			return result;
		} catch (Exception e) {
			throw ExFactory.ex(AlertException::new, ErrorCode.ALERT_CALC_FAIL, java.util.Map.of("msgid", doc.getMsgid()));
		}
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
		if (privacyInfos == null) return Collections.emptyList();
		return privacyInfos.stream().filter(k -> loadPatterns.contains(k.getId())).toList();
	}

	@Data
	public static class AlertInfo {
		private String msgid;
		private ActionType action;
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
