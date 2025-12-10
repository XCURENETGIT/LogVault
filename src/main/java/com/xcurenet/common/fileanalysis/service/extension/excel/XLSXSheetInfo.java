package com.xcurenet.common.fileanalysis.service.extension.excel;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
@Component
public class XLSXSheetInfo {
	public SheetDetector.SheetInfo getSheetInfo(File file) {
		try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ)) {
			XSSFReader reader = new XSSFReader(pkg);
			AtomicInteger totalSheets = new AtomicInteger(0);
			List<String> hiddenSheets = new ArrayList<>();
			try (InputStream wbData = reader.getWorkbookData()) {
				SAXParserFactory factory = SAXParserFactory.newInstance();
				factory.setNamespaceAware(true);
				SAXParser saxParser = factory.newSAXParser();
				saxParser.parse(wbData, new DefaultHandler() {
					@Override
					public void startElement(String uri, String localName, String qName, Attributes atts) {
						boolean isSheet = "sheet".equals(localName) || "sheet".equals(qName) || (qName != null && qName.endsWith(":sheet"));
						if (isSheet) {
							totalSheets.incrementAndGet();
							String name = atts.getValue("name");
							String state = atts.getValue("state"); // "hidden" | "veryHidden" | null(visible)
							if ("hidden".equalsIgnoreCase(state) || "veryHidden".equalsIgnoreCase(state)) {
								hiddenSheets.add(name);
							}
						}
					}
				});
			}
			if (hiddenSheets.isEmpty()) return new SheetDetector.SheetInfo(totalSheets.get(), 0, null);
			else return new SheetDetector.SheetInfo(totalSheets.get(), hiddenSheets.size(), hiddenSheets);
		} catch (Exception e) {
			log.error("Error reading file: {}", file.getAbsolutePath(), e);
		}
		return new SheetDetector.SheetInfo();
	}
}
