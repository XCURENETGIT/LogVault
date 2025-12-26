package com.xcurenet.logvault.opensearch;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.xcurenet.logvault.module.util.ActionType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.Date;
import java.util.List;

@Data
@Document(indexName = "aegis-room", writeTypeHint = WriteTypeHint.FALSE)
public class AegisRoomDoc {
	@Id
	@Field("room_id")
	private String roomId;

	@Field("action")
	private ActionType action;

	@Field(name = "@timestamp", type = FieldType.Date, format = DateFormat.epoch_millis)
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
	private Date timestamp;

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
