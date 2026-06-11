package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.AccountVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Getter
@Log4j2
@Service
@RequiredArgsConstructor
public class AccountsLoader {

	private static final AtomicReference<Map<String, String>> ACCOUNT_MAP_REF = new AtomicReference<>(Collections.emptyMap());
	private static final AtomicReference<Map<String, String>> ACCOUNT_REGEX_MAP_REF = new AtomicReference<>(Collections.emptyMap());

	private final InfoLoaderService infoLoaderService;

	public void load() {
		long version = infoLoaderService.getAccountVersion();
		List<AccountVO> datas = infoLoaderService.getAccounts(version);
		log.info("INFO_LOAD | Rule Version : {} | Company Accounts Size: {}", version, datas.size());

		Map<String, String> accounts = new LinkedHashMap<>();
		Map<String, String> accountRegex = new LinkedHashMap<>();
		for (AccountVO item : datas) {
			log.debug("INFO_LOAD | Company Accounts: {}", item);
			if(Common.isEquals(item.getMatchType(),"N")){
				accounts.put(item.getServiceCd(),item.getCompanyAccount());
			}
			if(Common.isEquals(item.getMatchType(),"R")){
				accountRegex.put(item.getServiceCd(),item.getCompanyAccount());
			}
		}
		ACCOUNT_MAP_REF.set(accounts);
		ACCOUNT_REGEX_MAP_REF.set(accountRegex);
	}

	public static boolean isDetectAccount(String svcCd, String account) {
		if (svcCd == null || account == null) {
			return false;
		}

		// suffix 검사
		String regexAccounts = ACCOUNT_REGEX_MAP_REF.get().get(svcCd);
		if (!Common.isEmpty(regexAccounts)) {
			for (String suffix : regexAccounts.split(",")) {
				suffix = suffix.trim();

				if (suffix.isEmpty()) {
					continue;
				}

				if (account.endsWith(suffix)) {
					return true;
				}
			}
		}

		// 완전 일치 검사
		String companyAccounts = ACCOUNT_MAP_REF.get().get(svcCd);
		if (Common.isEmpty(companyAccounts)) {
			return false;
		}

		for (String str : companyAccounts.split(",")) {
			if (account.equals(str.trim())) {
				return true;
			}
		}

		return false;
	}

}
