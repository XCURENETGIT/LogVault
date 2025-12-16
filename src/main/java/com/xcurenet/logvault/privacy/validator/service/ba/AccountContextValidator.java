package com.xcurenet.logvault.privacy.validator.service.ba;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

import java.util.List;
import java.util.Locale;

public class AccountContextValidator implements DefaultCheck {
	private static final int MAX_DISTANCE = 30;
	private static final List<String> KEYWORDS = List.of("계좌번호", "계좌", "은행", "통장", "입금", "출금", "예금", "송금", "account", "bank", "acct", "iban", "swift", "routing");

	@Override
	public ValidationResult validate(final String input, final String context) {
		if (input == null || input.isBlank()) return ValidationResult.fail("INPUT_IS_NULL_OR_EMPTY");

		String ctx = normalize(context);
		int idx = ctx.indexOf(normalize(input));
		for (String keyword : KEYWORDS) {
			int k = ctx.indexOf(keyword);
			if (k >= 0 && Math.abs(k - idx) <= MAX_DISTANCE) {
				return ValidationResult.ok();
			}
		}
		return ValidationResult.fail("KEYWORD_TOO_FAR");
	}

	private String normalize(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
	}
}
