package com.xcurenet.logvault.module.worker;

import com.xcurenet.common.Constants;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.msg.MSGParser;
import com.xcurenet.common.types.AttachExtension;
import com.xcurenet.common.types.FileNameInfo;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.logvault.exception.IndexerException;
import com.xcurenet.logvault.exception.ParsingException;
import com.xcurenet.logvault.loader.type.UserInfo;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class MSGWorker extends AbstractLogVaultWorker {

	public MSGWorker(final ApplicationContext context, PriorityBlockingQueue<ScanData> queue, final AtomicBoolean run) {
		super(context, queue, run);
	}

	@Override
	protected void parse(ScanData data) throws ParsingException {
		MSGData msg = data.getMsgData();

		EmassDoc doc = new EmassDoc();
		doc.setMsgid(msg.getMsgid());
		doc.setTimestamp(new Date(msg.getCtime().getMillis()));
		doc.setCtime(msg.getCtime().toString(DateUtils.YYYYMMDDHHMMSS));
		doc.setLtime(DateUtils.formatToYYYYMMDDHHMMSS(data.getStart()));
		setService(msg, doc);
		setNetwork(msg, doc);
		setHttp(msg, doc);
		setBody(msg, doc);
		setAttach(msg, doc);
		setSize(doc);

		data.setEmassDoc(doc);
	}

	@Override
	protected void insaMapping(final ScanData data) {
		UserInfo info = insaManager.getUser(data);
		EmassDoc.User user = new EmassDoc.User();
		user.setIp(data.getMsgData().getSourceIp().toCanonicalAddr());
		if (info != null) {
			user.setId(info.getUserId());
			user.setName(info.getName());
			user.setCeo(CommonUtil.isEquals(info.getCeo(), "Y"));
			user.setDeptCode(info.getDeptCd());
			user.setDeptName(info.getDeptNm());
			user.setJikgubCode(info.getJikgubCd());
			user.setJikgubName(info.getJikgubNm());
		}
		data.getEmassDoc().setUser(user);
	}

	@Override
	protected void index(ScanData data) throws IndexerException {
		long startTime = System.currentTimeMillis();
		EmassDoc doc = data.getEmassDoc();
		template.save(doc, IndexCoordinates.of(conf.getIndexName() + doc.getCtime().substring(0, 8)));
		log.info("[MG_INDEX] {} | {}", doc.getMsgid(), DateUtils.duration(startTime));
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
		String port = doc.getNetwork().getDstPort() == 443 ? "" : ":" + CommonUtil.nvl(doc.getNetwork().getDstPort());
		http.setUrl(String.join("", "https://", CommonUtil.nvl(msg.getHost()), port, CommonUtil.nvl(msg.getUrl()), CommonUtil.nvl(msg.getQuery())));
		doc.setHttp(http);
	}

	private void setBody(MSGData msg, EmassDoc doc) {
		EmassDoc.Body body = new EmassDoc.Body();
		if (msg.getMsgFilePath() == null) return;

		File file = new File(msg.getMsgFilePath());
		if (!file.exists()) return;
		String text = CommonUtil.limitLength(FileUtil.getText(msg.getMsgFilePath()), conf.getTextLimitLength());
		text = CommonUtil.limitTokenLengthWithSpace(text, conf.getTextLimitToken());

		body.setSize(file.length());
		body.setText(text);
		doc.setBody(body);
	}

	private void setAttach(MSGData msg, EmassDoc doc) {
		final List<String> appFiles = CommonUtil.nvl(msg.getAppFile());
		final List<String> appFilePaths = CommonUtil.nvl(msg.getAppFilePath());
		final List<String> pcFiles = CommonUtil.nvl(msg.getPcFile());
		final List<String> pcFilePaths = CommonUtil.nvl(msg.getPcFilePath());
		final List<AttachExtension> extensions = CommonUtil.nvl(msg.getExtension());
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

			String name = CommonUtil.get(pcFiles, i);
			if (name == null) name = CommonUtil.get(appFiles, i);
			if (CommonUtil.isNotEmpty(name)) {
				if (StringUtils.containsAny(name, MSGParser.ERROR_CHAR)) {
					log.warn("[ERR_NAME] {} {}", doc.getMsgid(), name);
					name = CommonUtil.sanitizeFileName(name, MSGParser.ERROR_CHAR);
				}
				at.setName(name);
			}

			String srcPath = CommonUtil.get(appFilePaths, i);
			if (srcPath == null && CommonUtil.get(pcFiles, i) != null) srcPath = CommonUtil.get(pcFilePaths, i);
			at.setSrcPath(srcPath);

			File srcFile = (CommonUtil.isEmpty(srcPath) ? null : new File(Objects.requireNonNull(srcPath)));
			boolean exists = (srcFile != null && srcFile.exists());
			long fSize = exists ? srcFile.length() : 0L;
			at.setExist(exists);
			at.setSize(fSize);
			at.setHash(CommonUtil.digest(Constants.SHA256, srcPath));
			at.setHasName(getFileNameExist(i, extensions));

			String id = CommonUtil.get(appFiles, i);
			if (id == null) {
				String pcName = CommonUtil.get(pcFiles, i);
				id = (pcName != null) ? CommonUtil.toHexString(CommonUtil.md5(pcName)) : null;
			}
			at.setId(id);

			if (exists) existCnt++;
			sizeSum += fSize;
			attaches.add(at);
		}

		doc.setAttach(attaches.isEmpty() ? null : attaches);
		doc.setAttachCount(attaches.size());
		doc.setAttachExistCount(existCnt);
		doc.setAttachTotalSize(sizeSum);
	}

	private boolean getFileNameExist(int i, List<AttachExtension> extensions) {
		try {
			return extensions.get(i).isFileNameExist();
		} catch (Exception e) {
			log.warn("[NAME_EXIST] {}", e.getMessage());
		}
		return false;
	}

	private void setSize(EmassDoc doc) {
		if (doc.getBody() != null) doc.setSize(doc.getAttachTotalSize() + doc.getBody().getSize());
		else doc.setSize(doc.getAttachTotalSize());
	}
}