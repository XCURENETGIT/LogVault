package com.xcurenet.common.fileanalysis.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xcurenet.common.fileanalysis.service.extension.excel.SheetDetector;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileInfoVO {
	private String textPath;
	private List<String> imgPath;
	private boolean isEncrypted;
	private boolean isDrm;
	private String extension;
	private boolean isChangeExtension;
	private boolean isUnknownType;
	private ArchiveInfo archiveInfo;
	private OLEInfo oleInfo;
	private SheetDetector.SheetInfo sheetInfo;

	@Data
	@Builder
	public static class ArchiveInfo {
		private boolean isImageInArchive;
	}

	@Data
	@Builder
	public static class OLEInfo {
		private int oleCount;
	}
}
