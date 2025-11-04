package com.xcurenet.logvault.tool;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.msg.MSGParser;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.logvault.exception.ProcessDataException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SampleData {

	private static final String DECODER_INFO = "/users/las/msg/info/wmail";
	private static final String DECODER_DATA = "/users/las/msg/data";

	public static void main(String[] args) throws IOException, ProcessDataException {
		File dir = new File("./sample");
		System.out.println(dir.getAbsolutePath());
		File msg = getFile(dir, ".msg");
		File body = getFile(dir, ".txt");
		File header = getFile(dir, ".hdr");
		File[] attach = getFiles(dir, ".attach");

		for (int i = 0; i < 10000000; i++) {
			String nowStr = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
			String fileName = getInfoPath(replaceTimestampWithNow("WMAIL", msg.getName(), nowStr));

			System.out.println(fileName);
			File newMsg = new File(fileName);
			FileUtils.forceMkdir(newMsg.getParentFile());

			String txt = writeMsg(msg, nowStr);
			FileUtils.writeStringToFile(newMsg, txt, Charset.defaultCharset());

			MSGData data = MSGParser.parse(newMsg.getAbsolutePath());
			copyFile(body, new File(getDataPath(data.getMsgFile())));
			copyFile(header, new File(getDataPath(data.getHeader())));

			List<String> appFiles = data.getAppFile();
			for (int x = 0; x < attach.length; x++) {
				copyFile(attach[x], new File(getDataPath(appFiles.get(x))));
			}
			Common.sleep(1000);
		}
	}

	private static void copyFile(File src, File dest) throws IOException {
		System.out.println(src.getAbsolutePath() + " -> " + dest.getAbsolutePath());
		FileUtils.copyFile(src, dest);
	}

	private static String writeMsg(File msg, String nowStr) throws IOException {
		StringBuilder result = new StringBuilder();
		FileUtils.readLines(msg, Charset.defaultCharset()).forEach(line -> {
			if (line.startsWith("CTIME :")) {
				result.append("CTIME :").append(DateUtils.convertDateTimeYYYYMMDD(nowStr)).append("\n");
			} else if (line.startsWith("HDRFILE :")) {
				result.append("HDRFILE :").append(nowStr).append(line.split(":")[1].substring(15)).append("\n");
			} else if (line.startsWith("MSGFILE :")) {
				result.append("MSGFILE :").append(nowStr).append(line.split(":")[1].substring(15)).append("\n");
			} else if (line.startsWith("APPFILE")) {
				result.append(line.split(":")[0]).append(": ").append(nowStr).append(line.split(":")[1].substring(15)).append("\n");
			} else {
				result.append(line).append("\n");
			}
		});
		return result.toString();
	}

	private static File getFile(File dir, String ext) {
		File[] filteredFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(ext));
		if (filteredFiles != null) {
			for (File f : filteredFiles) {
				return f;
			}
		}
		return dir;
	}

	private static File[] getFiles(File dir, String ext) {
		return dir.listFiles((d, name) -> name.toLowerCase().endsWith(ext));
	}


	public static String replaceTimestampWithNow(String prefix, String filename, String nowStr) {
		int prefixLen = prefix.length();
		String oldTimestamp = filename.substring(prefixLen, prefixLen + 14);
		return filename.replace(oldTimestamp, nowStr);
	}

	public static String getDataPath(final String fileName) {
		if (Common.isEmpty(fileName)) return Common.EMPTY;
		return Common.makeFilepath(DECODER_DATA, Long.toString(Common.getSplitNum(fileName, 100)), fileName);
	}

	public static String getInfoPath(final String fileName) {
		if (Common.isEmpty(fileName)) return Common.EMPTY;
		return Common.makeFilepath(DECODER_INFO, Long.toString(Common.getSplitNum(fileName, 100)), fileName);
	}
}
