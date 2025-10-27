package com.xcurenet.logvault.fs;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Data
@Service
@RequiredArgsConstructor
public class FileProcessor {

	private final Config conf;

	private FileSystemService service;

	private final FileSystemService localFileSystem;

	private final FileSystemService minioFileSystem;

	@PostConstruct
	public void init() throws Exception {
		this.service = "local".equals(conf.getFileSystemType()) ? localFileSystem : minioFileSystem;
		service.init();
	}

	public boolean exists(final String path) {
		return service.exists(path);
	}

	public XcnFileStatus status(final String path) {
		return service.status(path);
	}

	public InputStream open(final String path) throws Exception {
		return service.open(path);
	}

	public boolean delete(final String path) {
		return service.delete(path);
	}

	public boolean deleteDirectory(final String path) {
		return service.deleteDirectory(path);
	}

	public void write(final String src, final String dst, final String fileName) throws Exception {
		service.write(src, dst, fileName);
	}

	public void writeText(final String src, final String text) throws Exception {
		service.writeText(src, text);
	}

	public void write(final String src, final InputStream is, final String fileName) throws Exception {
		service.write(src, is, fileName);
	}

	public long getTotalSpace(final String src) {
		if (Common.isEmpty(src)) return 0;
		return service.getTotalSpace(src);
	}

	public long getUsableSpace(final String src) {
		if (Common.isEmpty(src)) return 0;
		return service.getUsableSpace(src);
	}

	public long size(final String src) {
		if (Common.isEmpty(src)) return 0;
		return service.size(src);
	}
}