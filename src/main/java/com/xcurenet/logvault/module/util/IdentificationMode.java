package com.xcurenet.logvault.module.util;

public enum IdentificationMode {
	IP, PORT;

	public String getTypeCode() {
		return name().substring(0, 1);
	}

	@Override
	public String toString() {
		return getTypeCode();
	}
}
