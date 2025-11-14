package com.xcurenet.logvault.tool.cli.sample;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.msg.MSGParser;
import com.xcurenet.common.types.IP;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

@Log4j2
@CommandLine.Command(
		name = "sample",
		description = "Utility class for generating mock or sample data for testing.")
public class SampleData implements Callable<Integer> {

	@CommandLine.Parameters(
			arity = "1",
			paramLabel = "count of sample data.",
			description = "Please specify the count of sample data.")
	private int input;

	private static final String DECODER_INFO = "/users/las/msg/info/wmail";
	private static final String DECODER_DATA = "/users/las/msg/data";
	private static final String REPLACE_NAME = "DEBDA8FBC3951135ED28B45CFD0FAB8B";
	private static final SecureRandom SECURERANDOM = new SecureRandom();

	@Override
	public Integer call() throws Exception {
		for (int i = 1; i <= input; i++) {
			run();
			Common.sleep(1000);
		}
		return 0;
	}

	private void run() throws Exception {
		String oldIp = "01e13165";
		IP ip = new IP(getRandomIP());

		File dir = new File("./sample");
		File msg = getFile(dir, ".msg");
		File body = getFile(dir, ".txt");
		File header = getFile(dir, ".hdr");
		File[] attach = getFiles(dir, ".attach");
		String randomStr = randomHex32();
		String nowStr = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
		String fileName = getInfoPath(replaceTimestampWithNow("WMAIL", msg.getName(), nowStr, randomStr)).replace(oldIp, ip.toHexString());

		File newMsg = new File(fileName);
		FileUtils.forceMkdir(newMsg.getParentFile());

		String txt = writeMsg(msg, nowStr, randomStr, oldIp, ip);
		FileUtils.writeStringToFile(newMsg, txt, Charset.defaultCharset());
		Common.setAllPermissions(newMsg);
		log.info("MSG WRITE : {}", newMsg);

		MSGData data = MSGParser.parse(newMsg.getAbsolutePath());
		File bodyDest = new File(getDataPath(data.getMsgFile(), oldIp, ip.toHexString()));
		copyFile(body, bodyDest);
		log.info("BODY WRITE : {}", bodyDest);

		File hdDest = new File(getDataPath(data.getHeader(), oldIp, ip.toHexString()));
		copyFile(header, hdDest);

		List<String> appFiles = data.getAppFile();
		for (int x = 0; x < appFiles.size(); x++) {
			File attDest = new File(getDataPath(appFiles.get(x), oldIp, ip.toHexString()));
			copyFile(attach[x], attDest);

			if (appFiles.size() - 1 == x) log.info("ATTACH WRITE : {}\n", attDest);
			else log.info("ATTACH WRITE : {}", attDest);
		}
	}

	private static void copyFile(File src, File dest) throws IOException {
		FileUtils.copyFile(src, dest);
	}

	private static String writeMsg(File msg, String nowStr, String randomStr, final String oldIp, IP ip) throws IOException {
		StringBuilder result = new StringBuilder();
		FileUtils.readLines(msg, Charset.defaultCharset()).forEach(line -> {
			line = line.replace(REPLACE_NAME, randomStr);
			if (line.startsWith("CTIME :")) {
				result.append("CTIME : ").append(DateUtils.convertDateTimeYYYYMMDD(nowStr)).append("\n");
			} else if (line.startsWith("HDRFILE :")) {
				result.append("HDRFILE : ").append(nowStr).append(line.split(":")[1].substring(15).replace(oldIp, ip.toHexString())).append("\n");
			} else if (line.startsWith("MSGFILE :")) {
				result.append("MSGFILE : ").append(nowStr).append(line.split(":")[1].substring(15).replace(oldIp, ip.toHexString())).append("\n");
			} else if (line.startsWith("APPFILE")) {
				result.append(line.split(":")[0]).append(": ").append(nowStr).append(line.split(":")[1].substring(15).replace(oldIp, ip.toHexString())).append("\n");
			} else if (line.startsWith("SOURCEIP")) {
				result.append("SOURCEIP : ").append(ip.toString()).append("\n");
			} else if (line.startsWith("STYPE")) {
				result.append("STYPE : ").append(getRandomSVC()).append("\n");
			} else {
				result.append(line).append("\n");
			}
		});
		return result.toString();
	}

	private static String getRandomIP() {
		int last = ThreadLocalRandom.current().nextInt(12, 240);
		return "1.225.49." + last;
	}

	private static final List<String> SVC = List.of(
			"IAB", "IAC", "IAD", "IAN", "IAO", "IBA", "IBG", "IBI", "ICB", "ICC", "ICD", "ICE", "ICG", "ICH", "ICL", "ICP", "ICR",
			"ICS", "ICU", "ICW", "ICX", "IDA", "IDB", "IDM", "IDP", "IDT", "IEL", "IFE", "IGA", "IGB", "IGC", "IGE", "IGL", "IGM",
			"IGO", "IGP", "IGR", "IGS", "IGV", "IHF", "IHV", "IHW", "IIA", "IJL", "IKM", "IKR", "ILL", "ILM", "ILX", "IMA", "IME",
			"IMG", "IMJ", "IMS", "IMT", "INA", "INF", "INK", "INN", "INO", "IOC", "IOE", "IOO", "IOW", "IPE", "IPF", "IPH", "IPO",
			"IPP", "IPS", "IQB", "IRK", "IRM", "IRP", "ISB", "ISD", "ISG", "ISI", "ISN", "ISO", "ISR", "ITC", "ITG", "ITP", "ITY",
			"IUK", "IVR", "IWB", "IWD", "IWK", "IWT", "IXA", "IXO", "IYB", "IYC", "IYY"
	);

	private static String getRandomSVC() {
		int index = ThreadLocalRandom.current().nextInt(SVC.size());
		return SVC.get(index) + "S";
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


	public static String replaceTimestampWithNow(String prefix, String filename, String nowStr, String randomStr) {
		int prefixLen = prefix.length();
		String oldTimestamp = filename.substring(prefixLen, prefixLen + 14);
		return filename.replace(oldTimestamp, nowStr).replace(REPLACE_NAME, randomStr);
	}

	public static String getDataPath(final String fileName, final String oldIp, final String newIp) {
		if (Common.isEmpty(fileName)) return Common.EMPTY;
		String path = Common.makeFilepath(DECODER_DATA, Long.toString(Common.getSplitNum(fileName, 100)), fileName);
		return Objects.requireNonNull(path).replaceAll(oldIp, newIp);
	}

	public static String getInfoPath(final String fileName) {
		if (Common.isEmpty(fileName)) return Common.EMPTY;
		return Common.makeFilepath(DECODER_INFO, Long.toString(Common.getSplitNum(fileName, 100)), fileName);
	}

	public static String randomHex32() {
		byte[] bytes = new byte[16];
		SECURERANDOM.nextBytes(bytes);

		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02X", b));
		}
		return sb.toString();
	}
}
