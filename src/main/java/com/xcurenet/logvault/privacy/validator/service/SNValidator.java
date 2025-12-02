package com.xcurenet.logvault.privacy.validator.service;


import com.xcurenet.logvault.privacy.ResidentRegistrationNumber;
import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.PatternValidator;
import com.xcurenet.logvault.privacy.validator.service.sn.*;

import java.util.List;

public class SNValidator implements PatternValidator {
	private final List<SNCheck> checks = List.of(new RRNPatternValidator(), new DateValidator(), new GenderValidator(), new RegionValidator(), new ChecksumValidator());

	@Override
	public ValidationResult validate(final String value, final String context) {
		ResidentRegistrationNumber rrn = new ResidentRegistrationNumber(value, context);
		for (SNCheck check : checks) {
			ValidationResult result = check.validate(rrn);
			if (!result.valid()) return result;
		}
		return ValidationResult.ok();
	}
}