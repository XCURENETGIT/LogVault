package com.xcurenet.logvault.fs;

import com.xcurenet.common.Constants;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.crypto.Crypto;
import com.xcurenet.crypto.CryptoInputStream;
import com.xcurenet.logvault.conf.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

@Log4j2
@Service("localFileSystem")
@RequiredArgsConstructor
public class LocalFileSystem implements FileSystemService {

	protected final Config conf;

	@Override
	public void init() {
		log.info("INIT_LOCAL | Local File System");
	}

	@Override
	public XcnFileStatus status(String path) {
		try {
			if (!exists(path)) return null;
			Path p = Paths.get(path);
			BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
			return new XcnFileStatus(attrs.size(), attrs.isDirectory(), attrs.lastModifiedTime().toMillis(), null);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public boolean exists(final String path) {
		return Files.exists(Paths.get(path));
	}

	@Override
	public InputStream open(String path) throws Exception {
		StopWatch sw = DateUtils.start();
		Path p = Paths.get(path);
		InputStream in;
		if (conf.isEncryptEnable()) {
			Crypto crypto = new Crypto(conf.getEncryptKey(), conf.getEncyptCipher());
			in = new BufferedInputStream(new CryptoInputStream(crypto, Files.newInputStream(p)));
		} else {
			in = Files.newInputStream(p, StandardOpenOption.READ);
		}

		log.debug("AT_OPEN | {} | {}", path, DateUtils.stop(sw));
		return in;
	}

	@Override
	public boolean delete(String path) {
		try {
			Path p = Paths.get(path);
			return Files.deleteIfExists(p);
		} catch (Exception e) {
			log.warn("AT_DELETE | Error ", e);
		}
		return false;
	}

	@Override
	public boolean deleteDirectory(String path) {
		Path root = Paths.get(path);
		try {
			if (Files.notExists(root)) {
				return true;
			}

			try (var walk = Files.walk(root)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.deleteIfExists(p);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
			return true;
		} catch (Exception e) {
			log.warn("DIR_DELETE | Error ", e);
		}
		return false;
	}

	@Override
	public void write(final String src, final String dst, final String fileName) throws Exception {
		StopWatch sw = DateUtils.start();

		Path srcPath = Paths.get(src);
		Path dstPath = Paths.get(dst);
		if (dstPath.getParent() != null) {
			Files.createDirectories(dstPath.getParent());
		}

		long srcSize = Files.size(srcPath);
		try (InputStream fis = Files.newInputStream(srcPath, StandardOpenOption.READ);
		     OutputStream fos = Files.newOutputStream(dstPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			if (conf.isEncryptEnable()) {
				Common.copy(fis, false, Constants.SHA256, conf.getEncyptCipher(), conf.getEncryptKey(), srcSize, fos, null);
			} else {
				fis.transferTo(fos);
			}
			log.debug("AT_WRITE | {} {} | {} | {} | {}", fileName, src, dst, Common.convertFileSize(srcSize), DateUtils.stop(sw));
		}
	}

	@Override
	public void writeText(final String path, final String text) throws Exception {
		StopWatch sw = DateUtils.start();
		Path p = Paths.get(path);

		if (p.getParent() != null) {
			Files.createDirectories(p.getParent());
		}
		Files.writeString(p, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		log.debug("AT_WRITE | {} | {} | {}", path, Common.convertFileSize(text.length()), DateUtils.stop(sw));
	}

	@Override
	public void write(final String path, final InputStream is, final String fileName) throws Exception {
		StopWatch sw = DateUtils.start();
		Path p = Paths.get(path);

		if (p.getParent() != null) {
			Files.createDirectories(p.getParent());
		}

		try (OutputStream fos = Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			if (conf.isEncryptEnable()) {
				Common.copy(is, false, Constants.SHA256, conf.getEncyptCipher(), conf.getEncryptKey(), is.available(), fos, null);
			} else {
				is.transferTo(fos);
			}
			log.debug("AT_WRITE | {} | {} | {}", fileName, path, DateUtils.stop(sw));
		}
	}

	@Override
	public long getTotalSpace(String path) {
		try {
			Path p = Paths.get(path);
			return Files.getFileStore(p).getTotalSpace();
		} catch (IOException e) {
			log.warn("AT_TOTAL | {}", e.getMessage());
		}
		return 0L;
	}

	@Override
	public long getUsableSpace(String path) {
		try {
			Path p = Paths.get(path);
			return Files.getFileStore(p).getUsableSpace();
		} catch (IOException e) {
			log.warn("AT_USABLE | {}", e.getMessage());
		}
		return 0L;
	}

	@Override
	public long size(String path) {
		try {
			Path p = Paths.get(path);
			if (!Files.exists(p) || !Files.isRegularFile(p)) {
				return 0L;
			}
			return Files.size(p);
		} catch (Exception e) {
			log.warn("FILE_SIZE | {} | {}", path, e.getMessage());
			return 0L;
		}
	}
}
