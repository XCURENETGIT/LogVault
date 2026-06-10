package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.AccountsLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
@Log4j2
@Service
@RequiredArgsConstructor

public class AccountAnalysis {
	public void detect(final ScanData scanData) {
		if (scanData == null || scanData.getEmassDoc() == null) {
			log.warn("{} | scanData or emassDoc is null", ErrorCode.KEYWORD_MSGDATA_NULL.toString());
			return;
		}
		detect(scanData.getEmassDoc(), scanData.getMsgData());
	}

	public void detect(final EmassDoc doc, final MSGData msg) {
		if (doc == null) {
			log.warn("{} | EmassDoc is null", ErrorCode.KEYWORD_MSGDATA_NULL);
			return;
		}

		if (msg == null) {
			return;
		}

		String svc = msg.getSvc();
		if (Common.isEmpty(svc) || svc.length() < 4) {
			return;
		}

		if (Common.isEmpty(msg.getAccount())) {
			return;
		}

		EmassDoc.User user = doc.getUser();
		if (user == null) {
			return;
		}

		String svc12 = "" + svc.charAt(0) + svc.charAt(3);
		user.setCompanyAccount(AccountsLoader.isDetectCode(svc12, msg.getAccount()));
	}


}
