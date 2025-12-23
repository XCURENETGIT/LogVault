package com.xcurenet.common.error;

import lombok.Getter;

@Getter
public enum ErrorCode {

	/* =========================
	 * Crypto
	 * ========================= */
	ENC_KEY_FAIL("LVT-0001", "Encryption Key file is missing."),
	/* =========================
	 * Parser
	 * ========================= */
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
	PARSER_MSGFILE_NULL("LVT-1012", "MSG MSG FILE (body) is null"),
	PARSER_STYPE_NULL("LVT-1013", "MSG STYPE is null or cannot be derived"),

	PARSER_FILENAME_FAIL("LVT-1014", "MSG File Name Parsing Failed"),
	PARSER_SVC_NULL("LVT-1015", "MSG File SVC is null"),
	PARSER_SVC_INVALID("LVT-1016", "MSG File SVC is invalid"),
	PARSER_INVALID("LVT-1998", "Invalid parser"),
	PARSER_MSG_FAIL("LVT-1999", "EDCDoc Parsing Failed"),

	/* =========================
	 * Index
	 * ========================= */
	INDEX_NAME_NULL("LVT-2002", "Index name is null"),
	INDEX_DATA_NULL("LVT-2003", "Index data is null"),
	INDEX_CONNECT_FAIL("LVT-2004", "OpenSearch Service Connection Failed"),
	INDEX_DEL_NAME_NULL("LVT-2005", "Index name is null"),
	INDEX_DEL_INVALID("LVT-2006", "Refusing to delete '*' or '_all'"),
	INDEX_DEL_SYSTEM("LVT-2007", "Refusing to delete system/hidden indices"),
	INDEX_DEL_FAIL("LVT-2009", "Refusing to delete system/hidden indices"),

	INDEX_SAVE_FAIL("LVT-2999", "Failed to index document into {index}"),

	/* =========================
	 * File Send / Write
	 * ========================= */
	FILE_WRITE_TEXT_FAIL("LVT-3001", "Failed to write text file"),
	FILE_WRITE_STREAM_FAIL("LVT-3002", "Failed to send file"),
	FILE_MSG_SEND_FAIL("LVT-3003", "Failed to send MSG file"),
	FILE_BODY_SEND_FAIL("LVT-3004", "Failed to send body file"),

	FILE_SEND_FAIL("LVT-3998", "Failed to send file"),
	EMBED_SEND_FAIL("LVT-3999", "Failed to send file"),

	/* =========================
	 * INSA
	 * ========================= */
	INSA_MSG_NULL("LVT-4001", "ScanData.getMsgData is null"),
	INSA_SIP_NULL("LVT-4002", "source IP is null"),
	INSA_MAPPING_FAIL("LVT-4999", "INSA Mapping Failed"),

	/* =========================
	 * Scan Name
	 * ========================= */
	SCAN_NAME_INVALID("LVT-5001", "Invalid file name: {info}"),
	SCAN_NAME_PART_COUNT("LVT-5002", "File name component count mismatch: {count}"),
	SCAN_NAME_HEADER_INVALID("LVT-5003", "File name header (Type+Time) is invalid: {value}"),
	SCAN_NAME_HEX_INVALID("LVT-5004", "File name IP Hex string is invalid: {value}"),
	SCAN_NAME_PORT_RANGE("LVT-5005", "File name Port is out of range (0-65535): {value}"),
	SCAN_NAME_PORT_FORMAT("LVT-5006", "File name Port is not numeric: {value}"),
	SCAN_NAME_SEQ_INVALID("LVT-5007", "File name Sequence is not numeric: {value}"),
	SCAN_NAME_HOST_EMPTY("LVT-5008", "File name Host field is empty: {field}"),

	/* =========================
	 * File Analysis
	 * ========================= */
	FILE_ANALYSIS_SIZE("LVT-6001", "File size measurement failed."),

	/* =========================
	 * Attach Analysis
	 * ========================= */
	ATTACH_MSGDATA_NULL("LVT-6101", "ScanData or EmassDoc is null"),
	ATTACH_LIST_NULL("LVT-6102", "Attach list is null"),
	ATTACH_FILE_NOT_EXIST("LVT-6103", "Attach file does not exist"),
	ATTACH_TEXT_EXTRACT_FAIL("LVT-6104", "Failed to extract text from attach"),
	ATTACH_IMAGE_EXTRACT_FAIL("LVT-6105", "Failed to extract embedded image"),
	ATTACH_SHEET_INFO_FAIL("LVT-6106", "Failed to extract excel sheet info"),
	ATTACH_THUMBNAIL_FAIL("LVT-6107", "Failed to create thumbnail"),
	ATTACH_MKDIR_FAIL("LVT-6108", "Failed to create image directory"),

	/* =========================
	 * ML
	 * ========================= */
	ML_ANALYSIS_BODY_ERROR("LVT-7001", "Internal Server Error"),
	ML_ANALYSIS_ATTACH_ERROR("LVT-7002", "Internal Server Error"),
	ML_ANALYSIS_ERROR("LVT-7999", "ML Analysis Failed"),

	/* =========================
	 * Privacy / PII Analysis  ✅ 신규
	 * ========================= */
	PRIVACY_MSGDATA_NULL("LVT-7201", "ScanData or EmassDoc is null"),
	PRIVACY_REGEX_SCAN_FAIL("LVT-7202", "Privacy regex scan failed"),
	PRIVACY_REGEX_RESULT_NULL("LVT-7203", "Privacy regex scan result is null"),
	PRIVACY_DETECT_CODE_INVALID("LVT-7204", "Privacy detect code is not allowed"),
	PRIVACY_THRESHOLD_NOT_MET("LVT-7205", "Privacy detection count is below threshold"),
	PRIVACY_ENCRYPT_FAIL("LVT-7206", "Privacy data encryption failed"),
	PRIVACY_ML_API_ERROR("LVT-7207", "Privacy ML API request failed"),
	PRIVACY_ML_RESPONSE_INVALID("LVT-7208", "Privacy ML API response is invalid"),
	PRIVACY_ML_VERIFY_FAIL("LVT-7209", "Privacy ML verification failed"),
	PRIVACY_UNKNOWN_ERROR("LVT-7299", "Privacy unknown failed"),

	/* =========================
	 * Workday
	 * ========================= */
	WORKDAY_MSGDATA_NULL("LVT-8001", "ScanData.msgData is null"),
	WORKDAY_CTIME_NULL("LVT-8002", "ScanData.msgData.ctime is null"),
	WORKDAY_EMASSDOC_NULL("LVT-8003", "EmassDoc is null"),
	WORKDAY_SET_FAIL("LVT-8099", "Failed to set working day information"),

	/* =========================
	 * User-Agent
	 * ========================= */
	UA_MSGDATA_NULL("LVT-8101", "ScanData.msgData is null"),
	UA_HEADER_PATH_NULL("LVT-8102", "MSG header path is null"),
	UA_HEADER_FILE_NOT_FOUND("LVT-8103", "HTTP header file not found"),
	UA_HEADER_READ_FAIL("LVT-8104", "HTTP header file read failed"),
	UA_HEADER_PARSE_FAIL("LVT-8105", "HTTP header parsing failed"),
	UA_EMASSDOC_HTTP_NULL("LVT-8106", "EmassDoc.http is null"),
	UA_AGENT_PARSE_FAIL("LVT-8198", "User-Agent parsing failed"),
	UA_ANALYSIS_FAIL("LVT-8199", "User-Agent analysis failed"),

	/* =========================
	 * Keyword
	 * ========================= */
	KEYWORD_MSGDATA_NULL("LVT-8201", "ScanData or EmassDoc is null"),
	KEYWORD_LOADER_NULL("LVT-8202", "KeywordLoader or matcher is null"),
	KEYWORD_MATCH_FAIL("LVT-8203", "Keyword matching failed"),
	KEYWORD_ATTACH_INVALID("LVT-8204", "Attach name or text is invalid"),

	/* =========================
	 * Common
	 * ========================= */
	UNKNOWN_ERROR("LVT-9999", "Unknown error");

	private final String code;
	private final String messageTemplate;

	ErrorCode(String code, String messageTemplate) {
		this.code = code;
		this.messageTemplate = messageTemplate;
	}

	public static String fromCode(ErrorCode code) {
		if (code == null) return UNKNOWN_ERROR.getCode();
		String codeVal = code.getCode();
		for (ErrorCode e : values()) {
			if (e.code.equalsIgnoreCase(codeVal)) {
				return e.getCode();
			}
		}
		return UNKNOWN_ERROR.getCode();
	}
}