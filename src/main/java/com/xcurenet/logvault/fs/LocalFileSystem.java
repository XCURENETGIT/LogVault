package com.xcurenet.logvault.fs;

import com.xcurenet.common.Constants;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

@Log4j2
@Service("localFileSystem")
@RequiredArgsConstructor
public class LocalFileSystem implements FileSystemService {
	protected final Config conf;

	@Override
	public void init() {
		log.info("[INIT_LOCAL] Local File System");
	}

	@Override
	public XcnFileStatus status(String path) {
		try {
			if (!exists(path)) return null;
			BasicFileAttributes attrs = Files.readAttributes(new File(path).toPath(), BasicFileAttributes.class);
			return new XcnFileStatus(attrs.size(), attrs.isDirectory(), attrs.lastModifiedTime().toMillis(), null);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public boolean exists(final String path) {
		return Files.exists(new File(path).toPath());
	}

	@Override
	public InputStream open(String path) throws Exception {
		StopWatch sw = DateUtils.start();
		InputStream inputStream = new FileInputStream(path);
		log.debug("[AT_OPEN] {} | {}", path, DateUtils.stop(sw));
		return inputStream;
	}

	@Override
	public boolean delete(String path) {
		try {
			return Files.deleteIfExists(new File(path).toPath());
		} catch (Exception e) {
			log.warn("[AT_DELETE] Error ", e);
		}
		return false;
	}

	@Override
	public boolean deleteDirectory(String path) {
		try {
			FileUtils.deleteDirectory(new File(path));
			return true;
		} catch (Exception e) {
			log.warn("[DIR_DELETE] Error ", e);
		}
		return false;
	}

	@Override
	public void write(final String src, final String dst, final String fileName) throws Exception {
		StopWatch sw = DateUtils.start();
		File srcFile = new File(src);
		File dstFile = new File(dst);
		Files.createDirectories(dstFile.getParentFile().toPath());

		try (FileInputStream fis = new FileInputStream(srcFile); FileOutputStream fos = new FileOutputStream(dstFile)) {
			if (conf.isEncryptEnable()) {
				Common.copy(fis, false, Constants.SHA256, conf.getEncyptCipher(), conf.getEncryptKey(), srcFile.length(), fos, null);
			} else {
				fis.transferTo(fos);
			}
			log.debug("[AT_WRITE] | {} {} | {} | {} | {}", fileName, src, dst, Common.convertFileSize(srcFile.length()), DateUtils.stop(sw));
		}
	}

	@Override
	public void writeText(final String path, final String text) throws Exception {
		StopWatch sw = DateUtils.start();
		Files.writeString(new File(path).toPath(), text, StandardCharsets.UTF_8);
		log.debug("[AT_WRITE] {} | {} | {}", path, Common.convertFileSize(text.length()), DateUtils.stop(sw));
	}

	@Override
	public void write(final String path, final InputStream is, final String fileName) throws Exception {
		StopWatch sw = DateUtils.start();
		try (FileOutputStream fos = new FileOutputStream(path)) {
			if (conf.isEncryptEnable()) {
				Common.copy(is, false, Constants.SHA256, conf.getEncyptCipher(), conf.getEncryptKey(), is.available(), fos, null);
			} else {
				is.transferTo(fos);
			}
			log.debug("[AT_WRITE] {} | {} | {}", fileName, path, DateUtils.stop(sw));
		}
	}

	@Override
	public long getTotalSpace(String path) {
		try {
			return Files.getFileStore(new File(path).toPath()).getTotalSpace();
		} catch (IOException e) {
			log.warn("[AT_TOTAL] {}", e.getMessage());
		}
		return 0L;
	}

	@Override
	public long getUsableSpace(String path) {
		try {
			return Files.getFileStore(new File(path).toPath()).getUsableSpace();
		} catch (IOException e) {
			log.warn("[AT_USABLE] {}", e.getMessage());
		}
		return 0L;
	}

	@Override
	public long size(String path) {
		try {
			return new File(path).length();
		} catch (Exception e) {
			log.warn("[FILE_SIZE]: {} | {}", path, e.getMessage());
			return 0L;
		}
	}
}
