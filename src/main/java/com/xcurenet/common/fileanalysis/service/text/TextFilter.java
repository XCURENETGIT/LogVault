package com.xcurenet.common.fileanalysis.service.text;

import com.xcurenet.common.fileanalysis.service.option.Options;
import com.xcurenet.common.io.LimitedBufferedReader;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tomcat.util.http.fileupload.util.LimitedInputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Component
@RequiredArgsConstructor
public class TextFilter {
	private final Config conf;
	private static final int ATTACH_LIMIT = 1024 * 1024 * 100;
	private static final int DEPTH = 5;
	public static final String IMG_DIR = "_img/";
	private static final String OPTIONS = "-SN3OPTION_ARCHIVE_EXTRACT -SN3OPTION_EXTENSION_NO_CHECK -SN3OPTION_EMBEDED_OLE_FILTER -SN3OPTION_EMBEDED_OLE_SEPARATE -SN3OPTION_COMPRESSION_ARCHIVE_LEVEL_LIMIT -SN3OPTION_EMBEDED_ATTACH_FILTER -SN3OPTION_MAIL_ATTACH_FILTER -SN3OPTION_XML_TAG_FILTER -SN3OPTION_NO_USE_SPACE_REMOVER -DEPTH %d -IMG_PATH %s";

	private static final Set<String> IGNORE_EXTS = Set.of("office_zip", "gul", "mpeg", "mp3", "asf", "ra", "rm", "tiff", "tif", "png", "gif", "jpg", "bmp", "pcx", "mid", "wav", "avi", "pds");
	public static final String[] COMPRESS_EXT = new String[]{"zip", "zipx", "7z", "rar", "tar", "gz"};
	public static final Set<String> IMAGE_EXTS = Set.of("tiff", "tif", "png", "gif", "jpg", "jpeg", "bmp", "pcx", "dcx", "jb2", "jfif", "jp2", "jpc", "j2k", "pdf");

	private static final String FILE_MARKER = "..FILE:";
	private static final String OLE_START = "~*~OLE START~*~";
	private static final String OLE_END = "~*~OLE END~*~";
	private static final Pattern ERROR_PATTERN = Pattern.compile("ERROR_CODE:\\s*\\d+");

	public TextFilterResult filter(final Options options, final String filePath) {
		StopWatch sw = DateUtils.start();

		Process proc = null;
		LimitedBufferedReader reader = null;

		String ext = null;
		int oleStartCount = 0, oleEndCount = 0;
		boolean hasImages = false;
		StringBuilder content = new StringBuilder();
		List<String> images = new ArrayList<>();
		String imgPath = new File(filePath).getParent() + "/" + IMG_DIR;
		try {
			proc = startProcess(filePath, options.getImagePath());
			reader = new LimitedBufferedReader(new InputStreamReader(new LimitedInputStream(proc.getInputStream(), ATTACH_LIMIT) {
				@Override
				protected void raiseError(long l, long l1) {
				}
			}));

			// 첫 라인 (파일 확장자)
			ext = reader.readLine();
			if (ext != null && !IGNORE_EXTS.contains(ext)) {
				long lastActive = System.currentTimeMillis();
				while (true) {
					if (reader.ready()) {
						String line = reader.readLine();
						LineParseResult parsed = parseLine(line, content);

						if (parsed.oleStart) oleStartCount++;
						if (parsed.oleEnd) oleEndCount++;
						if (parsed.hasImage) hasImages = true;

						lastActive = System.currentTimeMillis();
					} else if (Common.isProcessExited(proc)) {
						break;
					}

					if (Common.diffTime(lastActive) > 180000) {
						log.warn("ATT_TEXT | {} | Timeout 180 sec. | {}", options.getMsgId(), filePath);
						break;
					}
				}
			}
		} catch (Exception e) {
			log.error("ERR_TEXT | {} | {}", filePath, e);
		} finally {
			IOUtils.closeQuietly(reader);
			if (proc != null) proc.destroy();
			File[] imageFiles = new File(imgPath).listFiles();
			if (imageFiles != null) images = Arrays.stream(imageFiles).map(File::getAbsolutePath).toList();
		}

		log.debug("TX___END {} | {} | {}", new File(filePath).getName(), DateUtils.stop(sw), Common.getSummaryText(content.toString()));
		int oleCount = Math.min(oleStartCount, oleEndCount);
		return new TextFilterResult(ext, oleCount, hasImages, images, content.toString());
	}

	// ===============================
	// Helper Methods
	// ===============================
	private Process startProcess(final String filePath, final String imgPath) throws IOException {
		List<String> command = new ArrayList<>(List.of(conf.getXutf8Path()));
		command.addAll(Arrays.asList(String.format(OPTIONS, DEPTH, imgPath).split(" ")));
		command.add(filePath);

		log.debug("TX_START | {}", String.join(" ", command));
		return new ProcessBuilder(command).start();
	}

	private LineParseResult parseLine(String line, StringBuilder content) {
		if (line == null) return new LineParseResult();

		boolean oleStart = line.contains(OLE_START);
		boolean oleEnd = line.contains(OLE_END);
		boolean hasImage = false;

		// 파일명 추출 → 이미지 여부 확인
		if (line.startsWith(FILE_MARKER)) {
			String fileName = line.substring(FILE_MARKER.length());
			String ext = FilenameUtils.getExtension(fileName).toLowerCase();
			if (IMAGE_EXTS.contains(ext)) hasImage = true;
		}

		// 텍스트 누적 (첫 라인은 에러코드 제외)
		if (content.isEmpty()) {
			Matcher matcher = ERROR_PATTERN.matcher(line);
			if (!matcher.find()) content.append(line).append("\n");
		} else {
			content.append(line).append("\n");
		}
		return new LineParseResult(oleStart, oleEnd, hasImage);
	}

	private record LineParseResult(boolean oleStart, boolean oleEnd, boolean hasImage) {
		LineParseResult() {
			this(false, false, false);
		}
	}

	/**
	 * 확장자 탐지
	 *
	 * @param filepath 파일 경로
	 * @return 예상 확장자
	 * @throws IOException File NotFound
	 */
	public String getExtension(final String filepath) throws IOException {
		return getExtension(new File(filepath));
	}

	public String getExtension(final File file) throws IOException {
		StopWatch sw = DateUtils.start();
		LimitedBufferedReader reader = null;
		Process proc = null;
		String ext;
		try {
			List<String> command = new ArrayList<>();
			command.add(conf.getXutf8ExtPath());
			command.add(file.getPath());

			proc = new ProcessBuilder(command).start();
			reader = new LimitedBufferedReader(new InputStreamReader(new LimitedInputStream(proc.getInputStream(), ATTACH_LIMIT) {
				@Override
				protected void raiseError(long l, long l1) {
				}
			}));
			ext = reader.readLine();
			log.debug("EXPECTED | {} | {}", ext, DateUtils.stop(sw));
		} finally {
			IOUtils.closeQuietly(reader);
			if (proc != null) {
				proc.destroy();
			}
		}
		return ext;
	}
}
