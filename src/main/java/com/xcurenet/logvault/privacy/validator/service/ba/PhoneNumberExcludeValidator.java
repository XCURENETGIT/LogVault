package com.xcurenet.logvault.privacy.validator.service.ba;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;

public class PhoneNumberExcludeValidator implements DefaultCheck {
	private static final String PHONE_REGEX = "^(01[016789])[-\\s]?\\d{3,4}[-\\s]?\\d{4}$";

	@Override
	public ValidationResult validate(String input, String context) {
		if (input == null) return ValidationResult.fail("INPUT_NULL");

		String normalized = input.replaceAll("[^0-9]", "");
		// 휴대폰 번호 길이 + 시작 패턴
		if (normalized.length() == 11 && normalized.startsWith("01")) {
			return ValidationResult.fail("PHONE_NUMBER_PATTERN");
		}
		return ValidationResult.ok();
	}
}
