package com.xcurenet.logvault.privacy.validator.service;


import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.PatternValidator;
import com.xcurenet.logvault.privacy.validator.service.ba.AccountContextValidator;
import com.xcurenet.logvault.privacy.validator.service.ba.DigitValidator;
import com.xcurenet.logvault.privacy.validator.service.ba.PhoneNumberExcludeValidator;

import java.util.List;

public class BAValidator implements PatternValidator {
	private final List<DefaultCheck> checks = List.of(new DigitValidator(), new PhoneNumberExcludeValidator(), new AccountContextValidator());

	@Override
	public ValidationResult validate(final String value, final String context) {
		for (DefaultCheck check : checks) {
			ValidationResult result = check.validate(value, context);
			if (!result.valid()) return result;
		}
		return ValidationResult.ok();
	}
}
