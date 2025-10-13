package com.xcurenet.logvault.fs;

import com.xcurenet.common.Constants;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
		long startTime = System.currentTimeMillis();
		InputStream inputStream = new FileInputStream(path);
		log.debug("[AT_OPEN] {} | {}", path, DateUtils.duration(startTime));
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
	public void write(final String src, final String dst, final String fileName) throws Exception {
		long startTime = System.currentTimeMillis();
		File srcFile = new File(src);
		File dstFile = new File(dst);
		Files.createDirectories(dstFile.getParentFile().toPath());

		try (FileInputStream fis = new FileInputStream(srcFile); FileOutputStream fos = new FileOutputStream(dstFile)) {
			if (conf.isEncryptEnable()) {
				CommonUtil.copy(fis, false, Constants.SHA256, conf.getEncyptCipher(), conf.getEncryptKey(), srcFile.length(), fos, null);
			} else {
				fis.transferTo(fos);
			}
			log.debug("[AT_WRITE] | {} {} | {} | {} | {}", fileName, src, dst, CommonUtil.convertFileSize(srcFile.length()), DateUtils.duration(startTime));
		}
	}

	@Override
	public void writeText(final String path, final String text) throws Exception {
		long startTime = System.currentTimeMillis();
		Files.writeString(new File(path).toPath(), text, StandardCharsets.UTF_8);
		log.debug("[AT_WRITE] {} | {} | {}", path, CommonUtil.convertFileSize(text.length()), DateUtils.duration(startTime));
	}

	@Override
	public void write(final String path, final InputStream is, final String fileName) throws Exception {
		long startTime = System.currentTimeMillis();
		try (FileOutputStream fos = new FileOutputStream(path)) {
			if (conf.isEncryptEnable()) {
				CommonUtil.copy(is, false, Constants.SHA256, conf.getEncyptCipher(), conf.getEncryptKey(), is.available(), fos, null);
			} else {
				is.transferTo(fos);
			}
			log.debug("[AT_WRITE] {} | {} | {}", fileName, path, DateUtils.duration(startTime));
		}
	}
}
