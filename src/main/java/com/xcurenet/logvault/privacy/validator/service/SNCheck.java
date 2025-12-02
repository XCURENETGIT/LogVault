package com.xcurenet.logvault.privacy.validator.service;


import com.xcurenet.logvault.privacy.ResidentRegistrationNumber;
import com.xcurenet.logvault.privacy.ValidationResult;

public interface SNCheck {
	ValidationResult validate(ResidentRegistrationNumber rrn);
}
