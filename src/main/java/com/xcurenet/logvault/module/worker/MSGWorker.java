package com.xcurenet.logvault.module.worker;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.types.FileNameInfo;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.logvault.exception.IndexerException;
import com.xcurenet.logvault.exception.ParsingException;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

		data.setEmassDoc(doc);
	}

	@Override
	protected void insaMapping(final ScanData data) {
		// TODO document why this method is empty
	}

	@Override
	protected void index(ScanData data) throws IndexerException {
		// TODO document why this method is empty
	}

	private void setService(MSGData msg, EmassDoc doc) {
		EmassDoc.Service service = new EmassDoc.Service();
		char[] chars = msg.getSvc().toCharArray();
		service.setSvc(msg.getSvc());
		service.setSvc1(String.valueOf(chars[0]));
		service.setSvc2(String.valueOf(chars[1]));
		service.setSvc3(String.valueOf(chars[2]));
		service.setSvc4(String.valueOf(chars[3]));
		service.setSvc12("" + chars[0] + chars[1]);
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
		http.setUrl(String.join("", CommonUtil.nvl(msg.getHost()), CommonUtil.nvl(msg.getUrl()), CommonUtil.nvl(msg.getQuery())));
		doc.setHttp(http);
	}

	private void setBody(MSGData msg, EmassDoc doc) {
		EmassDoc.Body body = new EmassDoc.Body();
		if (msg.getMsgFilePath() == null) return;

		File file = new File(msg.getMsgFilePath());
		if (!file.exists()) return;

		body.setSize(file.length());
		body.setText(FileUtil.getText(msg.getMsgFilePath()));
		doc.setBody(body);
	}

	private void setAttach(MSGData msg, EmassDoc doc) {
		if (msg.getAppFile() == null || msg.getAppFile().isEmpty()) return;

		long attachTotalSize = 0;
		List<EmassDoc.Attach> attaches = new ArrayList<>();
		for (int i = 0; i < msg.getAppFile().size(); i++) {
			String appFile = msg.getAppFile().get(i);
			String appFilePath = msg.getAppFilePath().get(i);
			String name = getAttachName(msg, appFile, i);

			EmassDoc.Attach attach = new EmassDoc.Attach();
			attach.setId(appFile);
			attach.setName(name);
			if (appFilePath != null) {
				File file = new File(appFilePath);
				if (file.exists()) {
					attachTotalSize += file.length();
					attach.setSize(file.length());
				}
			}
			attaches.add(attach);
		}
		if (!attaches.isEmpty()) doc.setAttach(attaches);
		doc.setAttachTotalSize(attachTotalSize);
	}

	private static String getAttachName(MSGData msg, String name, int i) {
		try {
			name = msg.getPcFile().get(i);
		} catch (Exception e) {
			//ignore
		}
		return name;
	}
}