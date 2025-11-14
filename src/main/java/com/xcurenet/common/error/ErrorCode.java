package com.xcurenet.common.error;

import lombok.Getter;

@Getter
public enum ErrorCode {
	CONFIG_INVALID("LVT-0001", "Invalid configuration: {detail}"),

	PARSER_READ_FAIL("LVT-1001", "MSG File Read Failed"),
	PARSER_TEXT_NULL("LVT-1002", "MSG File is null"),
	PARSER_WORK_FAIL("LVT-1003", "MSG File Parsing Failed"),

	PARSER_CTIME_NULL("LVT-1004", "MSG CTIME is null"),
	PARSER_SIP_NULL("LVT-1005", "MSG SOURCE IP is null"),
	PARSER_SPORT_NULL("LVT-1006", "MSG SOURCE PORT is null"),
	PARSER_DIP_NULL("LVT-1007", "MSG DEST IP is null"),
	PARSER_HOST_NULL("LVT-1008", "MSG HOST is null"),
	PARSER_URL_NULL("LVT-1009", "MSG URL is null"),
	PARSER_QUERY_NULL("LVT-1010", "MSG Query is null"),

	PARSER_QUERY_TOO_LONG("LVT-1010", "MSG URL query is too long"),
	PARSER_HEADER_NULL("LVT-1011", "MSG HDRFILE is null"),
	PARSER_MSGFILE_NULL("LVT-1012", "MSG MSGFILE (body) is null"),
	PARSER_STYPE_NULL("LVT-1013", "MSG STYPE is null or cannot be derived"),


	PARSER_FILENAME_FAIL("LVT-1014", "MSG File Name Parsing Failed"),
	PARSER_INVALID("LVT-1999", "Invalid parser"),


	INDEX_NAME_NULL("LVT-2002", "Index name is null"),
	INDEX_DATA_NULL("LVT-2003", "Index data is null"),
	INDEX_CONNECT_FAIL("LVT-2004", "OpenSearch Service Connection Failed"),
	INDEX_DEL_NAME_NULL("LVT-2005", "Index name is null"),
	INDEX_DEL_INVALID("LVT-2006", "Refusing to delete '*' or '_all'"),
	INDEX_DEL_SYSTEM("LVT-2007", "Refusing to delete system/hidden indices"),
	INDEX_DEL_FAIL("LVT-2009", "Refusing to delete system/hidden indices"),

	INDEX_SAVE_FAIL("LVT-2999", "Failed to index document into {index}"),

	FILE_WRITE_TEXT_FAIL("LVT-3001", "Failed to write text file"),
	FILE_WRITE_STREAM_FAIL("LVT-3002", "Failed to send file"),
	FILE_MSG_SEND_FAIL("LVT-3003", "Failed to send MSG file"),
	FILE_BODY_SEND_FAIL("LVT-3004", "Failed to send body file"),

	FILE_SEND_FAIL("LVT-3999", "Failed to send file"),



	UNKNOWN_ERROR("LVT-9999", "Unknown error");

	private final String code;
	private final String messageTemplate;

	ErrorCode(String code, String messageTemplate) {
		this.code = code;
		this.messageTemplate = messageTemplate;
	}

	public static ErrorCode fromCode(ErrorCode code) {
		if (code == null) return UNKNOWN_ERROR;
		String codeVal = code.getCode();
		for (ErrorCode e : values()) {
			if (e.code.equalsIgnoreCase(codeVal)) {
				return e;
			}
		}
		return UNKNOWN_ERROR;
	}
}