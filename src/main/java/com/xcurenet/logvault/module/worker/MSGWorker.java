package com.xcurenet.logvault.module.worker;

import com.xcurenet.common.Constants;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.msg.MSGParser;
import com.xcurenet.common.types.AttachExtension;
import com.xcurenet.common.types.FileNameInfo;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.logvault.exception.IndexerException;
import com.xcurenet.logvault.exception.InsaMappingException;
import com.xcurenet.logvault.exception.ParsingException;
import com.xcurenet.logvault.loader.type.UserInfo;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.util.ActionType;
import com.xcurenet.logvault.module.util.IdentificationMode;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class MSGWorker extends AbstractWorker {

	public MSGWorker(final ApplicationContext context, PriorityBlockingQueue<ScanData> queue, final AtomicBoolean run) {
		super(context, queue, run);
	}

	@Override
	protected void parse(ScanData data) throws ParsingException {
		MSGData msg = data.getMsgData();
		if (msg == null)
			throw ExFactory.ex(ParsingException::new, ErrorCode.PARSER_MSG_FAIL, Map.of("info", data.getFilePath()));

		try {
			EmassDoc doc = new EmassDoc();
			doc.setMsgid(msg.getMsgid());
			doc.setAction(ActionType.valueOf(msg.getAction()));
			doc.setRootMtr(msg.getRootMtr());
			doc.setParentMtr(msg.getParentMtr());

			if (msg.getCtime() == null)
				throw ExFactory.ex(ParsingException::new, ErrorCode.PARSER_CTIME_NULL, Map.of("info", msg.getInfoText()));

			doc.setTimestamp(new Date(msg.getCtime().getMillis()));
			doc.setCtime(msg.getCtime().toString(DateUtils.YYYYMMDDHHMMSS));
			doc.setLtime(DateUtils.formatToYYYYMMDDHHMMSS(data.getStart()));

			if (msg.getSvc() == null)
				throw ExFactory.ex(ParsingException::new, ErrorCode.PARSER_SVC_NULL, Map.of("info", msg.getInfoText()));
			if (msg.getSvc().length() != 4)
				throw ExFactory.ex(ParsingException::new, ErrorCode.PARSER_SVC_INVALID, Map.of("info", msg.getInfoText()));

			setService(msg, doc);
			setNetwork(msg, doc);
			setHttp(msg, doc);
			setBody(msg, doc);
			setAttach(msg, doc);
			setSize(doc);

			String mlUsed = "N";
			if (Common.isNotEquals(msg.getAction(), "BLOCK") && conf.isMlApiEnable() && Common.isEquals(doc.getService().getSvc3(), "S")) mlUsed = "P";
			doc.setProcessStatus(EmassDoc.ProcessStatus.builder().ocr("N").ml(mlUsed).build()); //기본 OCR, ML 처리 대상여부, 처리 상태 값, 차단은 첨부 없어서 OCR은 대상아님.

			if (doc.getService() != null) { //생성형 AI 서비스중 수신 서비스의 경우 1밀리세컨드를 추가하여 Sort 처리를 한다.
				if (Common.isEquals("R", doc.getService().getSvc3())) {
					doc.getTimestamp().setTime(doc.getTimestamp().getTime() + 1);
				}
			}
			data.setEmassDoc(doc);
		} catch (Exception e) {
			throw ExFactory.ex(ParsingException::new, ErrorCode.PARSER_MSG_FAIL, Map.of("info", msg.getInfoText()), e);
		}
	}

	@Override
	protected void insaMapping(final ScanData data) throws InsaMappingException {
		if (data.getMsgData() == null) {
			throw ExFactory.ex(InsaMappingException::new, ErrorCode.INSA_MSG_NULL, Map.of("info", data));
		}
		if (data.getMsgData().getSourceIp() == null) {
			throw ExFactory.ex(InsaMappingException::new, ErrorCode.INSA_SIP_NULL, Map.of("info", data.getMsgData().getInfoText()));
		}
		if (data.getEmassDoc().getUser() != null) { //이미 처리된 상태라면
			return;
		}

		EmassDoc.User user = new EmassDoc.User();
		user.setIp(data.getMsgData().getSourceIp().toCanonicalAddr());
		try {
			if (conf.getUserIdentificationMode() == IdentificationMode.PORT && Common.isNumeric(data.getFileNameInfo().getDeviceName())) {
				user.setProxyPort(Common.nvz(data.getFileNameInfo().getDeviceName()));
			}

			UserInfo info = insaManager.getUser(data);
			if (info != null) {
				user.setId(info.getUserId());
				user.setName(info.getName());
				user.setCeo(Common.isEquals(info.getCeo(), "Y"));
				user.setDeptCode(info.getDeptCd());
				user.setDeptName(info.getDeptNm());
				user.setJikgubCode(info.getJikgubCd());
				user.setJikgubName(info.getJikgubNm());
			}
		} catch (Exception e) {
			//필수 srcip값이 있는 상황의 로직의 오류는 기본 처리는 하도록 한다.
			log.warn("{} | SRCIP={} err={}", ErrorCode.INSA_MAPPING_FAIL.toString(), data.getMsgData().getSourceIp(), e.toString(), e);
		}
		data.getEmassDoc().setUser(user);
	}

	@Override
	protected void roomId(final ScanData data) {
		EmassDoc doc = data.getEmassDoc();
		if (Common.isNotEquals(doc.getService().getSvc1(), "I")) return;

		String userId = doc.getUser() == null ? null : doc.getUser().getId();
		if (userId == null) userId = doc.getNetwork().getSrcIp();

		String roomId = Common.toBase64((doc.getService().getSvc12() + "_" + userId).getBytes(StandardCharsets.UTF_8));
		doc.setRoomId(roomId);
	}

	@Override
	protected void index(ScanData data) throws IndexerException {
		indexService.index(data.getEmassDoc());
	}

	@Override
	protected void alert(ScanData data) {
		alertService.send(data);
	}

	@Override
	protected boolean task(ScanData data) {
		return pipelineManager.send(data);
	}

	private void setService(MSGData msg, EmassDoc doc) {
		EmassDoc.Service service = new EmassDoc.Service();
		char[] chars = msg.getSvc().toCharArray();
		service.setSvc(msg.getSvc());
		service.setSvc1(String.valueOf(chars[0]));
		service.setSvc2(chars[1] + "" + chars[2]);
		service.setSvc3(String.valueOf(chars[3]));
		service.setSvc12(service.getSvc1() + service.getSvc2());
		doc.setService(service);
	}

	private void setNetwork(MSGData msg, EmassDoc doc) {
		FileNameInfo fileNameInfo = msg.getFileNameInfo();
		EmassDoc.Network network = new EmassDoc.Network();
		network.setProtocol(msg.getProtocol());
		network.setSrcPort(fileNameInfo.getSrcPort());
		network.setSrcIp(fileNameInfo.getSrcIP().toCanonicalAddr());
		network.setDstPort(fileNameInfo.getDstPort());
		network.setDstIp(fileNameInfo.getDstIP().toCanonicalAddr());
		doc.setNetwork(network);
	}

	private void setHttp(MSGData msg, EmassDoc doc) {
		if (msg.getHost() == null) return;

		EmassDoc.Http http = new EmassDoc.Http();
		http.setUrl(String.join("", "https://", Common.nvl(msg.getHost()), ":", Common.nvl(doc.getNetwork().getDstPort()), Common.nvl(msg.getUrl()), Common.nvl(msg.getQuery())));
		doc.setHttp(http);
	}

	private void setBody(MSGData msg, EmassDoc doc) {
		if (msg.getMsgFile() == null) return;

		Path filePath = Path.of(conf.getPath(msg.getMsgFile()));
		if (!Files.exists(filePath)) return;

		try {
			String text = Common.limitLength(FileUtil.getText(filePath.toString()), conf.getTextLimitLength());
			text = Common.limitTokenLengthWithSpace(text, conf.getTextLimitToken());
			text = Common.unescapeJava(text);
			log.debug("BDY_TEXT | {}", Common.getSummaryText(text));

			EmassDoc.Body body = new EmassDoc.Body();
			body.setExtension(FileUtil.getExtension(filePath.getFileName().toString()));
			body.setPath(conf.getDestPath(msg.getCtime(), msg.getMsgid(), filePath.getFileName().toString()));
			body.setSize(Files.size(filePath));
			body.setText(text);

			doc.setBody(body);
		} catch (Exception e) {
			log.error("BODY_PARSE_FAIL | {} | {}", msg.getMsgid(), e.getMessage(), e);
		}
	}

	private void setAttach(MSGData msg, EmassDoc doc) throws IOException {
		final List<String> appFiles = Common.nvl(msg.getAppFile());
		final List<String> pcFiles = Common.nvl(msg.getPcFile());
		final List<AttachExtension> extensions = Common.nvl(msg.getExtension());
		final int count = Math.max(appFiles.size(), pcFiles.size());

		if (count == 0) {
			doc.setAttach(null);
			doc.setAttachCount(0);
			doc.setAttachExistCount(0);
			doc.setAttachTotalSize(0L);
			return;
		}

		int existCnt = 0;
		long sizeSum = 0L;
		List<EmassDoc.Attach> attaches = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			EmassDoc.Attach at = new EmassDoc.Attach();
			String name = Common.get(pcFiles, i);
			if (name == null) name = Common.get(appFiles, i);
			if (Common.isNotEmpty(name)) {
				if (StringUtils.containsAny(name, MSGParser.ERROR_CHAR)) {
					log.warn("ERR_NAME | {} {}", doc.getMsgid(), name);
					name = Common.sanitizeFileName(name, MSGParser.ERROR_CHAR);
				}
				at.setName(name);
			}

			long size = 0;
			boolean exists = false;

			String srcPathStr = Common.get(appFiles, i);
			if (srcPathStr != null) {
				Path srcPath = Path.of(conf.getPath(srcPathStr));
				at.setSrcPath(srcPath.toAbsolutePath().toString());

				exists = Files.exists(srcPath);
				if (exists) {
					size = Files.size(srcPath);
					String hash = Common.digest(Constants.SHA256, srcPath.toAbsolutePath().toString());
					at.setHash(hash);
					at.setPath(conf.getDestPath(msg.getCtime(), msg.getMsgid(), srcPath.getFileName().toString()));
				}
			}

			at.setSize(size);
			at.setExist(exists);

			boolean hasName = getFileNameExist(i, extensions);
			at.setHasName(hasName);

			String ext = Optional.ofNullable(at.getName()).map(FilenameUtils::getExtension).map(String::toLowerCase).orElse(null);
			at.setExtension(ext);

			String id = Common.get(appFiles, i);
			if (id == null) {
				String pcName = Common.get(pcFiles, i);
				id = (pcName != null) ? Common.toHexString(Common.md5(pcName)) : null;
			}
			at.setId(id);

			if (exists) existCnt++;
			sizeSum += size;
			attaches.add(at);
		}

		doc.setAttach(attaches);
		doc.setAttachCount(attaches.size());
		doc.setAttachExistCount(existCnt);
		doc.setAttachTotalSize(sizeSum);
	}

	private boolean getFileNameExist(int i, List<AttachExtension> extensions) {
		if (extensions == null || i < 0 || i >= extensions.size()) return false;
		AttachExtension ae = extensions.get(i);
		return ae != null && ae.isFileNameExist();
	}

	private void setSize(EmassDoc doc) {
		if (doc.getBody() != null) doc.setSize(doc.getAttachTotalSize() + doc.getBody().getSize());
		else doc.setSize(doc.getAttachTotalSize());
	}
}