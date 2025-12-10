package com.xcurenet.common.fileanalysis.service.extension.excel;

import lombok.extern.log4j.Log4j2;
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument;
import org.odftoolkit.odfdom.doc.table.OdfTable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Component
public class ODSSheetInfo {
	public SheetDetector.SheetInfo getSheetInfo(File file) {
		SheetDetector.SheetInfo sheetInfo = new SheetDetector.SheetInfo();
		try (OdfSpreadsheetDocument ods = OdfSpreadsheetDocument.loadDocument(file)) {
			int totalSheets = ods.getSpreadsheetTables().size();
			int hiddenSheets = 0;
			List<String> hiddenSheetNames = new ArrayList<>();
			for (OdfTable sheet : ods.getSpreadsheetTables()) {
				String sheetName = sheet.getTableName();
				boolean hidden2 = sheet.getOdfElement().getAttribute("table:style-name").equalsIgnoreCase("ta2");
				String displayAttr = sheet.getOdfElement().getAttribute("table:display");
				boolean hidden = "false".equalsIgnoreCase(displayAttr);
				if (hidden || hidden2) {
					hiddenSheets++;
					hiddenSheetNames.add(sheetName);
				}
			}
			sheetInfo.setSheetTotal(totalSheets);
			sheetInfo.setSheetHiddenTotal(hiddenSheets);
			sheetInfo.setHiddenSheetNames(hiddenSheetNames);
		} catch (Exception e) {
			log.error("Error reading file: {}", file.getAbsolutePath(), e);
		}
		return sheetInfo;
	}
}
