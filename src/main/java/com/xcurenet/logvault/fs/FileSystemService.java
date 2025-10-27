package com.xcurenet.logvault.fs;

import java.io.InputStream;

public interface FileSystemService {

	void init() throws Exception;

	XcnFileStatus status(final String path);

	boolean exists(final String path);

	InputStream open(final String path) throws Exception;

	boolean delete(final String path);

	boolean deleteDirectory(final String path);

	void write(final String src, final String dst, final String fileName) throws Exception;

	void writeText(final String path, final String text) throws Exception;

	void write(final String path, final InputStream is, final String fileName) throws Exception;

	long getTotalSpace(final String path);

	long getUsableSpace(final String path);

	long size(final String path);
}
