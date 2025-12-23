package com.xcurenet.logvault.opensearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.util.List;

@Data
@Document(indexName = "aegis-room", writeTypeHint = WriteTypeHint.FALSE)
public class AegisRoomDoc {
	@Id
	@Field("room_id")
	private String roomId;

	@Field("recent_ctime")
	private String recentCtime;

	@Field("recent_msgid")
	private String recentMsgId;

	@Field("day")
	private EmassDoc.Day day;

	@Field("ml_result")
	private EmassDoc.MLResult mlResult;

	@Field("svc")
	private String svc;

	@Field("recent_message")
	private String recentMessage;

	@Field("user")
	private EmassDoc.User user;

	@Field("privacy_total") //탐지 개인정보 총 건수
	private int privacyTotal;

	@Field("privacy_info")
	private List<EmassDoc.PrivacyInfo> privacyInfo;
}
