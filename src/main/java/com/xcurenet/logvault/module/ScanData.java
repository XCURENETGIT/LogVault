package com.xcurenet.logvault.module;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.types.FileNameInfo;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Log4j2
public class ScanData {
	private final AtomicInteger scannerCount;
	public static final String CP949 = "CP949";
	public static final String UTF8 = "UTF8";
	public static final String ASCII = "ISO-8859-1";

	private Long start;
	private String filePath;
	private String fileName;
	private long lastModified;
	private long fileSize;
	private String dstDir;
	private FileNameInfo fileNameInfo;
	private MSGData msgData;
	private EmassDoc emassDoc;

	public ScanData(final File file, final AtomicInteger scannerCount) throws Exception {
		this.filePath = file.getPath();
		this.fileName = file.getName();
		this.lastModified = file.lastModified();
		this.fileSize = file.length();
		this.scannerCount = scannerCount;
		fileNameInfo = FileNameInfo.getInfo(file.getName());
	}


	public void incrementCount() {
		scannerCount.incrementAndGet();
	}

	public void decrementCount() {
		scannerCount.decrementAndGet();
	}
}
