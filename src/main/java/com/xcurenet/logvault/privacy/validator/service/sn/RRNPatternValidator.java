package com.xcurenet.logvault.privacy.validator.service.sn;

import com.xcurenet.logvault.privacy.ResidentRegistrationNumber;
import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.SNCheck;

import java.util.Set;
import java.util.regex.Pattern;

public class RRNPatternValidator implements SNCheck {
	private static final Set<String> FORBIDDEN_NUMBERS = Set.of("0000000000000", "1111111111111", "9999999999999");
	private static final Pattern CONTEXT_PATTERN = Pattern.compile("(?i)(http://|https://|ftp://|\\.zip|\\.jpg|\\.png|\\.gif|\\.exe|\\.json|\\.dll|\\.class|\\.manifest)");

	@Override
	public ValidationResult validate(ResidentRegistrationNumber rrn) {
		String normalized = rrn.getNormalized();
		String context = rrn.getContext();
		if (rrn.isInValidFormat()) return ValidationResult.fail("Invalid format : " + context);
		if (FORBIDDEN_NUMBERS.contains(normalized)) return ValidationResult.fail("Forbidden sequence : " + normalized);
		if (CONTEXT_PATTERN.matcher(context).find())
			return ValidationResult.fail("Context indicates URL/file : " + context);

		if (normalized.chars().distinct().count() == 1)
			return ValidationResult.fail("All digits identical : " + context);
		if (normalized.substring(0, 6).chars().distinct().count() == 1 && normalized.substring(6).chars().distinct().count() == 1)
			return ValidationResult.fail("Front/back identical : " + context);

		if ("0123456789".contains(normalized.substring(0, 6)) || "9876543210".contains(normalized.substring(0, 6)))
			return ValidationResult.fail("Sequential pattern : " + normalized);

		return ValidationResult.ok();
	}
}