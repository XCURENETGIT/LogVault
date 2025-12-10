package com.xcurenet.common.fileanalysis.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xcurenet.common.fileanalysis.service.extension.FileExtensionUtil;
import com.xcurenet.common.fileanalysis.service.extension.excel.SheetDetector;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextInfoVO {
	private String text;
	private int imagesCount;
	private List<EmbeddedImage> embeddedImage;
	private boolean isEncrypted;
	private boolean isDrm;
	private String extension;
	private boolean isChangeExtension;
	private boolean isUnknownType;
	private List<ArchiveInfo> archiveInfo;
	private OLEInfo oleInfo;
	private SheetDetector.SheetInfo sheetInfo;

	// 압축 파일내 이미지 정보
	@Data
	public static class ArchiveInfo {
		private String imgPaths;
		private String imgNames;
		private long imgSizes;
		private FileExtensionUtil.Extension imgExts;
		private String imgBase64s;
	}

	@Data
	@Builder
	public static class OLEInfo {
		private int oleCount;
	}

	@Data
	@Builder
	public static class EmbeddedImage {
		private String name;
		private String base64;
	}
}
