package com.xcurenet.common.utils;

import com.xcurenet.logvault.module.ScanData;

import java.util.Comparator;

public class FileTimeComparator implements Comparator<ScanData> {
	@Override
	public int compare(final ScanData f1, final ScanData f2) {
		return Long.signum(f1.getLastModified() - f2.getLastModified());
	}
}