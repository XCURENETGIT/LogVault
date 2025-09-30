package com.xcurenet.common.file;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Component
@RequiredArgsConstructor
public class AttachUtil {
	private final Config config;

	public static final Set<String> IGNORE_EXTS_DEFAULT = new HashSet<>(Arrays.asList("gul,mpeg,mp3,asf,ra,rm,tiff,tif,png,gif,jpg,bmp,pcx,mid,wav,avi,pds".split(",")));

	public static final String SYNAP_UNKNOWN = "unknown";

	public static final String XUTF8_EXT_BIN = "/users/logvalut/lib/xutf_8_ext";

	public static final String SNF_BIN = "/users/logvalut/lib/snf_exe";

	private static final int ATTACHLIMIT_DEFAULT = 1024 * 1024 * 100;

	private static final String[] ENCRYPT_CODE = new String[]{"30501", "33515", "30103", "30010", "50000", "44001", "31108", "63001", "34023", "35042", "58032", "61009", "71009"};
	private static final String[] UNSUPPORTED_CODE = new String[]{"30510", "30102", "31011", "40101", "40102", "63004", "94001", "33731", "35031", "40103", "40106", "33801", "33802"};

	//private static final String OPTION_COMMAND = "-SN3OPTION_ARCHIVE_EXTRACT -SN3OPTION_EXTENSION_NO_CHECK -SN3OPTION_EMBEDED_OLE_FILTER -SN3OPTION_EMBEDED_OLE_SEPARATE -SN3OPTION_COMPRESSION_ARCHIVE_LEVEL_LIMIT -DEPTH %s ";
	private static final String OPTION_COMMAND = " -TIMEOUT %s -U8 -O -SN3OPTION_COMPRESSION_ARCHIVE_LEVEL_LIMIT -SN3OPTION_NO_USE_SPACE_REMOVER -SN3OPTION_COMPRESSION_IGNORE_FILE_ERROR ";
	private static final String ZIP_FILE_LIST = " grep \"..FILE:\" %s | awk -F':' '{ $1=\"\"; sub(/^:/, \"\"); gsub(/^[ \\t]+|[ \\t]+$/, \"\", $0); print }' > %s ";


	/**
	 * 첨부파일 예상 확장자 추출
	 *
	 * @param file 대상 파일
	 * @return 예상 확장자
	 */
	public String getExtension(final File file) {
		List<String> command = new ArrayList<>();
		command.add(XUTF8_EXT_BIN);
		command.add(file.getAbsolutePath());

		long startTime = System.currentTimeMillis();
		String ext = CommonUtil.processFirstLine(command, ATTACHLIMIT_DEFAULT);
		log.info("[EXT_EXPECT] {} | {} | {}", ext, file.getAbsolutePath(), DateUtils.duration(startTime));
		if (ext != null) return ext.toLowerCase();
		else return null;
	}

	public AttachResult filter(final String msgId, final File file, final String attachHash) {
		if (file == null || !file.exists()) return null;
		String workingDir = CommonUtil.makeFilepath(getTmpRootPath(file.length()), msgId, attachHash);
		String txtPath = CommonUtil.makeFilepath(workingDir, file.getName() + ".txt");

		if (workingDir == null) return null;
		CommonUtil.mkdir(new File(workingDir));

		AttachResult result = new AttachResult();
		result.setLength(file.length());

		String option = String.format(OPTION_COMMAND, config.getExtractTextTimeout());
		List<String> command = new ArrayList<>();
		command.add("/bin/bash");
		command.add("-c");
		command.add(SNF_BIN + " " + option + " " + file.getAbsolutePath() + " " + txtPath);
		log.debug("[GET_TEXT] {} | {}", file.getAbsolutePath(), command);

		long startTime = System.currentTimeMillis();
		String response = CommonUtil.process(command, ATTACHLIMIT_DEFAULT);
		if (response.contains("ERROR")) {
			result.setEncrypted(isEncrypted(response));
			result.setUnSupported(isUnsupportedFile(response));
			if (result.isEncrypted()) result.setEncryptedCode(getErrorCode(response));
			if (result.isUnSupported()) result.setUnSupportCode(getErrorCode(response));
			log.info("[GET_TEXT] {} | {} | ERR_CODE : {}", msgId, file.getAbsolutePath(), getErrorCode(response));
		}

		if (txtPath != null && new File(txtPath).exists() && new File(txtPath).length() > 0) {
			result.setTextFile(new File(txtPath));
			log.info("[GET_TEXT] {} | {} ({} > {}) | {}", msgId, new File(txtPath).getName(), CommonUtil.convertFileSize(file.length()), CommonUtil.convertFileSize(new File(txtPath).length()), DateUtils.duration(startTime));
			result.setArchiveFiles(getFileList(txtPath));
		}
		return result;
	}

	private boolean isEncrypted(String input) {
		String code = getErrorCode(input);
		if (code == null) return false;
		return Arrays.asList(ENCRYPT_CODE).contains(code);
	}

	private boolean isUnsupportedFile(String input) {
		String code = getErrorCode(input);
		if (code == null) return false;
		return Arrays.asList(UNSUPPORTED_CODE).contains(code);
	}

	private String getErrorCode(final String input) {
		Pattern pattern = Pattern.compile("\\[(\\w+) *: *(\\d+)]");
		Matcher matcher = pattern.matcher(input);
		if (matcher.find()) {
			//String level = matcher.group(1);   // "ERROR"
			return matcher.group(2);    // "35042"
		}
		return null;
	}

	public List<String> getFileList(final String txtFile) {
		if (txtFile == null || !new File(txtFile).exists()) return null;
		try {
			List<String> command = new ArrayList<>();
			command.add("/bin/bash");
			command.add("-c");
			command.add(String.format(ZIP_FILE_LIST, txtFile, txtFile + ".files"));
			CommonUtil.process(command, ATTACHLIMIT_DEFAULT);
			List<String> lines = FileUtils.readLines(new File(txtFile + ".files"), StandardCharsets.UTF_8);
			if (lines.isEmpty()) return null;
			return lines;
		} catch (Exception e) {
			log.warn("", e);
		}
		return null;
	}

	private String getTmpRootPath(final long length) {
		return (length < config.getRamdiskLimit() && checkShmStatus()) ? config.getRamdiskPath() : config.getTempPath();
	}

	private boolean checkShmStatus() {
		final File dir = new File(config.getRamdiskPath());
		if (!dir.exists()) return false;
		return dir.getUsableSpace() >= config.getRamdiskLimit();
	}
}
