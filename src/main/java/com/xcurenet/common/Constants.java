package com.xcurenet.common;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Constants {
	// ENCODING
	public static final String CP949 = "CP949";
	public static final String UTF8 = "UTF8";
	public static final String UTF16LE = "UTF16LE";
	public static final String ASCII = "ISO-8859-1";

	// DIGEST
	public static final String SHA256 = "SHA-256";

	// Elasticsearch
	public static final String TOPIC_EMS_EDC_MESSAGE = "index";

	// Date Format
	public static final DateTimeFormatter DD = DateTimeFormat.forPattern("dd");
	public static final DateTimeFormatter HH = DateTimeFormat.forPattern("HH");
	public static final DateTimeFormatter MM = DateTimeFormat.forPattern("mm");
	public static final DateTimeFormatter HHMM = DateTimeFormat.forPattern("HHmm");
	public static final DateTimeFormatter HHMM_PATH = DateTimeFormat.forPattern("HH/mm");
	public static final DateTimeFormatter YYYY = DateTimeFormat.forPattern("yyyy");
	public static final DateTimeFormatter YYYYMM = DateTimeFormat.forPattern("yyyyMM");
	public static final DateTimeFormatter YYYYMMDD = DateTimeFormat.forPattern("yyyyMMdd");
	public static final DateTimeFormatter YYYYMMDDHH = DateTimeFormat.forPattern("yyyyMMddHH");
	public static final DateTimeFormatter YYYYMMDDHHMM = DateTimeFormat.forPattern("yyyyMMddHHmm");
	public static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormat.forPattern("yyyyMMddHHmmss");
	public static final DateTimeFormatter YYYYMMDDHHMMSSMILLIS = DateTimeFormat.forPattern("yyyyMMddHHmmss.SSS");
	public static final DateTimeFormatter DATETIME_CTIME = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss");
	public static final DateTimeFormatter DATETIME = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
	public static final DateTimeFormatter DATE = DateTimeFormat.forPattern("yyyy-MM-dd");
	public static final DateTimeFormatter DATETIMEMILLIS = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS");
	public static final DateTimeFormatter DATETIMEMILLISSYMBOL = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	public static final DateTimeFormatter FMT = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ssZZ");
	public static final DateTimeFormatter DATETIME_PATH = DateTimeFormat.forPattern("yyyy/MM/dd/HH/mm/ss");

	public static final String I = "I";
	public static final String O = "O";

	// Common Fields
	public static final String SVCDIR = "SVCDIR";
	public static final String MSGID = "MSGID";
	public static final String SVCKIND = "SVCKIND";
	public static final String MESSAGEID = "MESSAGEID";
	public static final String FILEPATH = "FILEPATH";
	public static final String FILENAME = "FILENAME";
	public static final String FILETIME = "FILETIME";

	public static final String PCAPNAME = "PCAPNAME";
	public static final String PCAPCNT = "PCAPCNT";

	public static final String DEFAULTCD = "C00-00";
	public static final String DEFAULTNM = "-";
	public static final String EMPTY_STRING = "";

	// info.MSG의 TYPE : {BODY, ATTACH, MIDINFO, NOMAP}
	public static final String TYPE = "TYPE";
	public static final String NOMAP = "NOMAP";

	// A:매핑키에 대응하는 모든 첨부 처리
	// B:매핑키에 대응하는 첫번째 첨부 처리 후 매핑키는 살린다.
	// C:임시저장 후 발송인 경우로 나중 발송건을 로깅 시 첨부 매핑 필요
	public static final String MAPPER = "MAPPER";
	public static final String ACTION = "ACTION";
	public static final String STYPE = "STYPE";
	public static final String PREFIX_AT = "at:";
	public static final String PREFIX_MD = "md:";
	public static final String PREFIX_CK = "ck:";
	public static final String REDIS_DELIMITER = ",";
	public static final String REDIS_KEY_BODY = "body";
	public static final String REDIS_KEY_USER = "user";
	public static final String REDIS_KEY_GOOGLE_DOCS = "gd:";
	public static final String REDIS_KEY_MAILKEY = "mk:";
	public static final String REDIS_KEY_XMSGKEY = "xk:";
	public static final String REDIS_KEY_OUTLOOK_MAILKEY = "omk:";
	public static final String REDIS_FAIL_KEY = "fail_";

	public static final String REDIS_KEY_OCR_STAT = "ocr:";        // ocr 임계치 설정 및 통계


	//  ABNL_DECT_PROC Condition
	public static final String REDIS_ABNL = "abnl:"; //판단 대기


	public static final String TOPIC_EDC = "edc";
	public static final String TOPIC_BODY = "body";
	public static final String TOPIC_OCR = "ocr";
	public static final String TOPIC_DRM = "drm";
	public static final String TOPIC_MESSENGER = "messenger";
	public static final String TOPIC_SEARCH = "ems_search_history";

	public static final String TOPIC_INDEX = "index";

	public static final String TOPIC_NOK_BODY = "nok_body";
	public static final String TOPIC_NOK_DRM = "nok_drm";
	public static final String TOPIC_NOK_OCR = "nok_ocr";
	public static final String TOPIC_NOK_MESSENGER = "nok_messenger";
	public static final String TOPIC_NOK_SEARCH = "nok_search";
	public static final String TOPIC_NOK_ANALYSIS = "nok_analysis";

	public static final String TOPIC_ANALYSIS = "analysis";
	public static final String TOPIC_ANALYSIS_RESULT = "analysis_result";

	public static final String TOPIC_ABNL = "abnl_detect";

	public static final String AT_CHECKER_CNT = "ATCHECKERCNT";
	public static final String COMPOSEID = "COMPOSEID";
	public static final String MAPPINGKEY = "MAPPINGKEY";
	public static final String COMPOSEKEY = "COMPOSEKEY";
	public static final String MAPPING_COMPLETE = "MAPPING_COMPLETE";

	// 2020-05-26 googleDocs 개발로 추가된 필드
	public static final String INDEX = "INDEX";
	public static final String ORGHDRFILE = "ORGHDRFILE";
	public static final String ORGINFOFILE = "ORGINFOFILE";

	public static final String EXTENSION = "EXTENSION";
	public static final String EXTENSION_ADD = "EXTENSION_ADD";
	public static final String AT_EXTENSION = "AT_EXTENSION";
	public static final String HDRFILE = "HDRFILE";
	public static final String MSGFILE = "MSGFILE";
	public static final String OPINION = "OPINION";
	public static final String APPFILE = "APPFILE";
	public static final String ISBODYIMAGE = "ISBODYIMAGE";
	public static final String APPFILEHASH = "APPFILEHASH";
	public static final String BEGINSEQ = "BEGINSEQ";
	public static final String PCFILE = "PCFILE";
	public static final String TOTALSIZE = "TOTALSIZE";
	public static final String PARTSIZE = "PARTSIZE";
	public static final String FSIZE = "FSIZE";
	public static final String MERGED_EXT = ".merged";

	public static final String CTIME = "CTIME";
	public static final String LTIME = "LTIME";
	public static final String EXPIRED = "EXPIRED";
	public static final String MERGED = "MERGED";

	public static final String MAILKEY = "MAILKEY";
	public static final String TIMESTAMP = "TIMESTAMP";

	// midinfo에 다음 항목들이 있으면 Redis에 등록 후 본문에서 치환
	public static final String FROMTMPID = "FROMTMPID_";
	public static final String TOTMPID = "TOTMPID_";
	public static final String CCTMPID = "CCTMPID_";
	public static final String BCCTMPID = "BCCTMPID_";
	public static final String USERTMPID = "USERTMPID_";
	public static final String PCFNAME = "PCFNAME_";
	public static final String ATTACH_BEGIN_SEQ = "ATTACH_BEGIN_SEQ_";
	public static final String TMP_ATTACH_BEGIN_SEQ = "TMP_ATTACH_BEGIN_SEQ_";
	public static final String TMP_ATTACH_BEGIN_SEQ2 = "TMP_ATTACH_BEGIN_SEQ2_";
	public static final String TMPFILENAME = "TMPFILENAME_";
	public static final String TMPFILENAME2 = "TMPFILENAME2_";

	// midinfo에 다음 항목들은 본문에 추가
	public static final String FROM = "FROM";
	public static final String TO = "TO";
	public static final String CC = "CC";
	public static final String BCC = "BCC";
	public static final String USER = "USER";
	public static final String SUBJECT = "SUBJECT";

	public static final String TRUE = "TRUE";
	public static final String FALSE = "FALSE";
	public static final String UNKNOWN = "unknown";
	public static final String NONAME = "noname";

	public static final String HAR_PATH = "har://";
	public static final String HAR = ".har";

	public static final String OCR = "OCR";
	public static final String DRM = "DRM";

	public static final String BODY_TEXT = "BODY_TEXT";
	public static final String BODYCONTENT = "BODYCONTENT";
	public static final String SEND_ID = "SEND_ID";
	public static final String RECV_ID = "RECV_ID";
	public static final String SENDER = "SENDER";
	public static final String BODY = "BODY";
	public static final String LOGIN_ID = "LOGIN_ID";
	public static final String PASSWORD = "PASSWORD";
	public static final String FLINK = "FLINK";
	public static final String FLINKKEY = "FLINKKEY";

	public static final String SERVER_FNAME = "SERVER_FNAME";
	public static final String ORG_FNAME = "ORG_FNAME";
	public static final String PROTOCOL = "PROTOCOL";

	public static final String OWA = "OWA";
	public static final String OWA_MDN = "OWA-MDN";
	public static final String RPC = "RPC";
	public static final String RPC_MDN = "RPC-MDN";
	public static final String FDN = "EP3-FDN";
	public static final String MDN = "EP3-MDN";

	public static final String ATTACHNAME_HYPHEN = "-"; // [2018-02-27:likebnb] EMS_PI.ATTACHNAME은 NOT NULL

	public static final String INTERNAL_ALL = "IA";
	public static final String EXTERNAL = "ET";
	public static final String INTERNAL = "IT";
	public static final String EXTERNAL_ALL = "EA";
	public static final String PARTNER = "PT";
	public static final String PARTNER_ALL = "PA";
	public static final String SELF_ONLY = "SO";
	public static final String SELF_INCLUDE = "SI";

	public static final String EMS_MESSAGE_COLLECTION = "EMS_MESSAGE_";

	public static final String SLINKEY = "SLINKEY";
	public static final String VLINKEY = "VLINKEY";

	public static final String SITECODE = "SITECODE";
	public static final String USERIP = "USERIP";

	public static final String HOST = "HOST";
	public static final String URL = "URL";
	public static final String URL_PARAM = "URL_PARAM";
	public static final String URL_PARAMS = "URL_PARAMS";

	public static final String ATTACHLISTS = "attachlists";
	public static final String HEADERS = "headers";
	public static final String ATTACHMENTS = "ATTACHMENTS";
}
