package com.xcurenet.logvault.module.analysis;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.AccountsLoader;
import com.xcurenet.logvault.loader.RuleLoader;
import com.xcurenet.logvault.loader.ServiceLoader;
import com.xcurenet.logvault.loader.type.BlockRuleJsonDto;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.util.ActionType;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Log4j2
@Service
@RequiredArgsConstructor

public class AccountAnalysis {
    private static final String RULE_TARGET_ATTACH = "ATTACH";
    private static final String RULE_TARGET_ACCOUNT = "ACCOUNT";

    private final ServiceLoader serviceLoader;
    private final RuleLoader ruleLoader;

    public void detect(ScanData scanData) {
        EmassDoc doc = scanData.getEmassDoc();
        MSGData msg = scanData.getMsgData();
        if (doc == null) {
            log.warn("{} | EmassDoc is null", ErrorCode.KEYWORD_MSGDATA_NULL);
            return;
        }

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
        setAccountBlockReason(doc, companyAccount);
    }

    private void setAccountBlockReason(EmassDoc doc, boolean companyAccount) {
        if (doc == null || doc.getAction() != ActionType.BLOCK || companyAccount || doc.getRuleSeq() == null) return;
        if (Common.isEquals(doc.getRuleTarget(), RULE_TARGET_ATTACH)) return;

        boolean accountBlockRule = ruleLoader.getRules().stream()
                .filter(Objects::nonNull)
                .filter(rule -> rule.getRuleSeq() != null && rule.getRuleSeq().intValue() == doc.getRuleSeq().intValue())
                .map(BlockRuleJsonDto.RuleEntry::getBlockNonCorpAccountYn)
                .anyMatch(value -> Common.isEquals(value, "Y"));

        if (accountBlockRule) {
            doc.setRuleTarget(RULE_TARGET_ACCOUNT);
        }
    }

}
