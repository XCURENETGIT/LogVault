package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.file.AttachUtil;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class AttachAnalysis {
	private final AttachUtil attachUtil;

	public void expectAttachExtension(final ScanData msg) {
//		List<Attach> attaches = msg.getAttach();
//		if (attaches == null) return;
//		for (Attach attach : attaches) {
//			if (attach.isExist() && attach.getExt() == null)
//				attach.setExt(attachUtil.getExtension(new File(attach.getSrcPath())));
//		}
	}

	public void setAttachText(final ScanData msg) {
//		List<Attach> attaches = msg.getAttach();
//		if (attaches == null) return;
//		for (Attach attach : attaches) {
//			if (attach.isExist()) {
//				if (CommonUtil.isEquals(AttachUtil.SYNAP_UNKNOWN, attach.getExt()) || AttachUtil.IGNORE_EXTS_DEFAULT.contains(CommonUtil.nvl(attach.getExt()))) {
//					attach.setUnsupported(true);
//					attach.setUnSupportCode("90000");
//					continue;
//				}
//
//				AttachResult result = attachUtil.filter(msg.getMsgId(), new File(attach.getSrcPath()), attach.getHash());
//				if (result == null) continue;
//				if (result.getTextFile() != null) attach.setTextSrcPath(result.getTextFile().getPath());
//				attach.setEncrypted(result.isEncrypted());
//				attach.setEncryptedCode(result.getEncryptedCode());
//				attach.setUnsupported(result.isUnSupported());
//				attach.setUnSupportCode(result.getUnSupportCode());
//				if (attach.getTextSrcPath() != null) {
//					attach.setArchivefile(result.getArchiveFiles());
//					attach.setTextPath(new File(attach.getPath()).getPath() + ".txt");
//				}
//				log.debug(result);
//			}
//		}
	}
}
