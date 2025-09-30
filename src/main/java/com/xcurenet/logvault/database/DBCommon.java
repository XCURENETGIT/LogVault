package com.xcurenet.logvault.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class DBCommon {
	private final static String EMS_MESSAGE = "EMS_MESSAGE_";
	private final static String EMS_LOG = "EMS_LOG_";

	public <T> List<T> getInfoToCollection(String collection, Class<T> t) {
		return findData(null, collection, t);
	}

	public <T> List<T> findData(Object query, String collection, Class<T> t) {
		log.debug("indexer query : {}", query);
		return new ArrayList<>();
	}

//	public <T> void saveEmsMessage(final DateTime ctime, T t) {
//		log.debug("[EMS_MESSAGE] SAVE : {}", t);
//	}
//
//	public void saveOldEmsMessage(final DateTime ctime, EDCData data) {
//		log.debug("[EMS_OLD_MESSAGE] SAVE : {}", data);
//	}

//	private JSONArray getRecvs(List<Recv> recvs) {
//		return new JSONArray();
//	}
}