package com.xcurenet.common.utils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.Properties;

public class PropConfig extends Properties {
	private static final long serialVersionUID = -4418544012942677590L;

	public PropConfig() {
		super();
	}

	public PropConfig(final String path) throws IOException {
		this();
		if (path != null) {
			loadResource(path);
		}
	}

	public PropConfig(final String[] paths) throws IOException {
		this();
		for (String path : paths) {
			final File file = new File(path);
			if (file.exists()) {
				loadResource(path);
			}
		}
	}

	public PropConfig(final Properties properties) {
		super.putAll(properties);
	}

	public void loadResource(final String path) throws IOException {
		if (path.charAt(0) != '/') {
			InputStream in = null;
			try {
				in = ClassLoader.getSystemResourceAsStream(path);
				load(in);
			} finally {
				IOUtils.closeQuietly(in);
			}
		} else {
			Reader reader = null;
			try {
				reader = new FileReader(path);
				load(reader);
			} finally {
				IOUtils.closeQuietly(reader);
			}
		}
	}

	public void addDefaultProperty(final Properties properties) {
		final Properties clone = (Properties) this.clone();
		this.putAll(properties);
		this.putAll(clone);
	}

	@Override
	public String getProperty(final String key) {
		return StringUtils.trim(super.getProperty(key));
	}

	@Override
	public String getProperty(final String key, final String defaultValue) {
		return StringUtils.trim(super.getProperty(key, defaultValue));
	}

	public boolean getPropertyBoolean(final String key, final boolean defaultValue) {
		final String value = getProperty(key);
		if (CommonUtil.parseBoolean(value)) {
			return true;
		}
		return defaultValue;
	}

	public int getPropertyInt(final String key, final int defaultValue) {
		final String value = getProperty(key);
		return StringUtils.isEmpty(value) ? defaultValue : Integer.parseInt(value);
	}

	public long getPropertyLong(final String key, final long defaultValue) {
		final String value = getProperty(key);
		return StringUtils.isEmpty(value) ? defaultValue : Long.parseLong(value);
	}

	public String[] getPropertyArray(final String key) {
		return getPropertyArray(key, null);
	}

	public String[] getPropertyArray(final String key, final String[] defaultValue) {
		final String value = StringUtils.trim(super.getProperty(key));
		return value == null ? defaultValue : value.split("[, ]+");
	}
}
