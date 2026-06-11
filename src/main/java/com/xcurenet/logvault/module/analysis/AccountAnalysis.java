package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.AccountsLoader;
import com.xcurenet.logvault.loader.ServiceLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor

public class AccountAnalysis {
    private final ServiceLoader serviceLoader;

    public void detect(ScanData scanData) {
        EmassDoc doc = scanData.getEmassDoc();
        MSGData msg = scanData.getMsgData();
        if (doc == null) {
            log.warn("{} | EmassDoc is null", ErrorCode.KEYWORD_MSGDATA_NULL);
            return;
        }

        if (Common.isNotEquals(doc.getService().getSvc3(), "S")) return; //발신 서비스만

        if (msg == null) {
            return;
        }

        String account = msg.getAccount() == null ? null : msg.getAccount().trim();
        if (Common.isEmpty(account)) {
            return;
        }

        EmassDoc.User user = doc.getUser();
        if (user == null) {
            user = new EmassDoc.User();
            doc.setUser(user);
        }

        String svc12 = doc.getService() == null ? null : doc.getService().getSvc12();
        if (Common.isEmpty(svc12) || !serviceLoader.isCompanyAccountUse(svc12)) {
            return;
        }

        boolean companyAccount = AccountsLoader.isDetectAccount(svc12, account);
        user.setCompanyAccount(companyAccount);
    }

}
