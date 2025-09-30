package com.xcurenet.logvault.module.util;

public enum LocationType {
	SUBJECT, BODY, FILENAME, ATTACH, META;

	public String getTypeCode() {
		return name().substring(0, 1);
	}

	@Override
	public String toString() {
		return getTypeCode();
	}
}
