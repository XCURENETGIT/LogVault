package com.xcurenet.common.fileanalysis.service.extension.excel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class SheetDetector {
	private final XLSSheetInfo xlsSheetInfo;
	private final XLSXSheetInfo xlsxSheetInfo;
	private final ODSSheetInfo odsSheetInfo;

	public SheetInfo detect(final File file, String ext) {
		try {
			return switch (ext) {
				case "ods" -> odsSheetInfo.getSheetInfo(file);
				case "xlsx", "cell" -> xlsxSheetInfo.getSheetInfo(file);
				case "xls" -> xlsSheetInfo.getSheetInfo(file);
				default -> null;
			};
		} catch (Exception e) {
			log.error("Error reading file: {}", file.getAbsolutePath(), e);
			return null;
		}
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class SheetInfo {
		private int sheetTotal;
		private int sheetHiddenTotal;
		private List<String> hiddenSheetNames;
	}
}
