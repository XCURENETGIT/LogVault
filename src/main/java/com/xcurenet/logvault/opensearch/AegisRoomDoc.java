package com.xcurenet.logvault.opensearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

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

	@Field("svc")
	private String svc;

	@Field("recent_message")
	private String recentMessage;

	@Field("user")
	private EmassDoc.User user;
}
