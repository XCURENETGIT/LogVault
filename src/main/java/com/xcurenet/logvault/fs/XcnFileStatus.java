package com.xcurenet.logvault.fs;

import com.xcurenet.common.utils.CommonUtil;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;

public class XcnFileStatus implements Serializable {
	private static final long serialVersionUID = 3615438876286670223L;

	public final URI path_uri;
	public long length;
	public boolean isdir;
	public short block_replication;
	public long blocksize;
	public long modification_time;
	public long access_time;
	public short permission_short;
	public String owner;
	public String group;
	public URI symlink_uri;

	public XcnFileStatus(final File file) throws IOException {
		this.length = file.length();
		this.isdir = file.isDirectory();
		this.modification_time = file.lastModified();
		this.path_uri = file.toURI();
		this.permission_short = getPermissionShort(file);
	}

	public XcnFileStatus(long length, boolean isdir, long time, URI uri) throws IOException {
		this.length = length;
		this.isdir = isdir;
		this.modification_time = time;
		this.path_uri = uri;
	}

	private short getPermissionShort(final File file) {
		// 자바는 Owner 퍼미션만 알 수 있다.
		short permission = 0;
		if (file.canRead()) {
			permission += 4;
		}
		if (file.canWrite()) {
			permission += 2;
		}
		if (file.canExecute()) {
			permission += 1;
		}
		return (short) (permission << 6);
	}

	public XcnFileStatus(final URI uri) {
		this.path_uri = uri;
	}

	public long getLen() {
		return this.length;
	}

	public void setLen(long length) {
		this.length = length;
	}

	public long getModificationTime() {
		return this.modification_time;
	}

	public static XcnFileStatus[] convert(final File[] listFiles) throws IOException {
		final XcnFileStatus[] fileStatus = new XcnFileStatus[listFiles.length];
		for (int i = 0; i < listFiles.length; i++) {
			fileStatus[i] = new XcnFileStatus(listFiles[i]);
		}
		return fileStatus;
	}

	public String getPath() {
		return path_uri.getPath();
	}

	public String getName() {
		return FilenameUtils.getName(getPath());
	}

	public String getParent() {
		final String path = getPath();
		return CommonUtil.getParentDir(path);
	}

	public boolean isDirectory() {
		return isdir;
	}

	public boolean isFile() {
		return !isdir;
	}

	public String getPermission() {
		return String.format("%d%d%d", permission_short >>> 6 & 0x7, permission_short >>> 3 & 0x7, permission_short & 0x7);
	}

	@Override
	public String toString() {
		return getPath();
	}

	@Override
	public int hashCode() {
		return getPath().hashCode();
	}
}
