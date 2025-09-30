package com.xcurenet.common.file;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.List;

@Data
public class AttachResult {
	private String ext;
	private long length;
	private String encryptedCode;
	private boolean encrypted;
	private boolean unSupported;
	private String unSupportCode;
	private File textFile;
	private List<String> archiveFiles;

	public boolean isValidExt() {
		return StringUtils.isNotEmpty(ext) && !AttachUtil.SYNAP_UNKNOWN.equalsIgnoreCase(ext);
	}
}
