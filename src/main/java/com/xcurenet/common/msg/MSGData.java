package com.xcurenet.common.msg;

import com.xcurenet.common.types.AttachExtension;
import com.xcurenet.common.types.EMail;
import com.xcurenet.common.types.FileNameInfo;
import com.xcurenet.common.types.IP;
import lombok.Data;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

@Data
public class MSGData {

	@FieldKey("SVCKIND") //EPU, EP4, OWA, RPC, EP3-FDN, EP3-MDN // STYPE 값이 없을 경우 SVCKIND을 활용 STYPE를 생성
	private String svcKind;

	@FieldKey("CTIME")
	private DateTime ctime;

	@FieldKey({"SOURCEIP"})
	private IP sourceIp;

	@FieldKey("SOURCEPORT")
	private int sourcePort;

	@FieldKey("DESTINATIONIP")
	private IP destinationIp;

	@FieldKey("HOST")
	private String host;

	@FieldKey("URL")
	private String url;

	@FieldKey({"URL_PARAMS"})
	private String query;

	@FieldKey("HDRFILE")
	private String header;

	private String headerPath;

	@FieldKey("MSGFILE")
	private String msgFile; //본문 파일 명

	private String msgFilePath; //본문 파일 명

	@FieldKey("MSGSIZE")
	private int bodySize; //본문 사이즈

	@FieldKey("CHARSET")
	private String bodyCharset; //본문 charset

	@FieldKey({"SENDER", "FROM", "SEND_ID"}) //SENDER(cmail), FROM(거의다...), SEND_ID(http_decoder nateOn, messenger_decoder, yahoo_decoder yahoo)
	private EMail from;

	@FieldKey("TO")
	private List<EMail> to = new ArrayList<>();

	@FieldKey("CC")
	private List<EMail> cc = new ArrayList<>();

	@FieldKey("BCC")
	private List<EMail> bcc = new ArrayList<>();

	@FieldKey("SUBJECT")
	private String subject;

	@FieldKey("PROTOCOL") // h2, http, websocket (http_decoder)
	private String protocol;

	@FieldKey("STYPE")
	private String svc;

	@FieldKey({"PCFILE", "ORG_FNAME"})
	private List<String> pcFile = new ArrayList<>();

	@FieldKey({"APPFILE", "SERVER_FNAME"})
	private List<String> appFile = new ArrayList<>();

	private List<String> appFilePath = new ArrayList<>();

	@FieldKey("EXTENSION")
	private List<AttachExtension> extension = new ArrayList<>();

	@FieldKey("FLINK") // mail, http, websocket
	private List<String> fLink = new ArrayList<>();

	@FieldKey("FLINKKEY")
	private List<String> fLinkKey = new ArrayList<>();

	@FieldKey("FSIZE") // mail, http, websocket
	private List<String> fSize = new ArrayList<>();

	@FieldKey("ISBODYIMAGE") // 첨부 파일이 본문 이미지 여부 (http_decoder)
	private List<String> bodyImage = new ArrayList<>();

	@FieldKey("EPHEADER")
	private String epHeader;

	@FieldKey("EPMSG_TYPE")
	private String epMsgType;

	@FieldKey("ACTION") // EP 전용 (SAVE, AUTO, BBS, APP, SEND, UNKNOWN), (http_decoder)
	private String action;

	@FieldKey("RESULT") // EP 전용 (GOOD, BAD) MysingleActor (http_decoder)
	private String result;

	@FieldKey("OPINION") // EP 전용 (결재 상신 내용이 담긴 파일 경로) MysingleActor (http_decoder)
	private String opinion;

	@FieldKey({"MSGKEY", "X-MTR", "MESSAGE_ID"}) // EP 전용 (KNOX 메일에서 주로 사용되며, 각 메일마다 부여되는 고유 MailID 값), 메신저 등의 고유 방 아이디
	private String msgKey;

	@FieldKey("ROOTMTR") // EP 전용 (최상위 메일 아이디)
	private String rootMtr;

	@FieldKey("PARENTMTR") // EP 전용 (KNOX 메일을 전달, 회신 등 사용할 때 바로 이전 MailID값)
	private String parentMtr;

	@FieldKey("SLINKEY") // EP 전용 (KNOX 메일을 전달, 회신 등 사용할 때 바로 이전 MailID값)
	private String slinkey;

	@FieldKey("VLINKEY") // EP 전용 (KNOX 메일을 전달, 회신 등 사용할 때 바로 이전 MailID값)
	private List<String> vlinkey;

	@FieldKey("PASSWORD") // 알수없음
	private String password;

	@FieldKey("USERIP") // 디코더에서 사용자를 찾을 수 있도록 남겨주는 IP 값 (사용자 아이피) http_decoder
	private IP userIp;

	@FieldKey({"USER", "LOGIN_ID"}) // 디코더에서 사용자를 찾을 수 있도록 남겨주는 IP 값 (bnr.yum@xcurenet.com 혹은 bnr.yum)
	private String loginId;

	@FieldKey("REPROCESS")
	private int reProcess = 0;

	private String infoFilePath; // INFO(MSG) 파일 경로

	private String infoText; // INFO(MSG) 파일 내용

	private FileNameInfo fileNameInfo; //INFO(MSG) 파일명 정보


	/**
	 * 아래는 파싱 내용을 정재한 내용
	 */

	private String msgid;   // MSGID
	private String ltime;   // logging time
}
