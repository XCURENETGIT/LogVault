package com.xcurenet.common.fileanalysis.service.extension.excel;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Component
public class XLSSheetInfo {

	public SheetDetector.SheetInfo getSheetInfo(File file) {
		SheetDetector.SheetInfo sheetInfo = new SheetDetector.SheetInfo();
		List<BoundSheetRecord> boundSheetRecords = new ArrayList<>();
		try (POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(file))) {
			HSSFRequest request = new HSSFRequest();
			request.addListenerForAllRecords(record -> {
				if (record instanceof BoundSheetRecord bsr) {
					boundSheetRecords.add(bsr);
				}
			});
			HSSFEventFactory factory = new HSSFEventFactory();
			factory.processWorkbookEvents(request, fs);
			int hiddenCount = 0;
			List<String> hiddenNames = new ArrayList<>();
			for (BoundSheetRecord bsr : boundSheetRecords) {
				sheetInfo.setSheetTotal(boundSheetRecords.size());
				if (bsr.isHidden()) {
					hiddenCount++;
					hiddenNames.add(bsr.getSheetname());
				}
			}
			sheetInfo.setSheetHiddenTotal(hiddenCount);
			if (!hiddenNames.isEmpty()) {
				sheetInfo.setHiddenSheetNames(hiddenNames);
			}
		} catch (Exception e) {
			log.error("Error reading file: {}", file.getAbsolutePath(), e);
			return new SheetDetector.SheetInfo();
		}
		return sheetInfo;
	}
}
