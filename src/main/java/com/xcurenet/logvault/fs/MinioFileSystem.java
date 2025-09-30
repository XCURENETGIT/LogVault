package com.xcurenet.logvault.fs;

import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Log4j2
@Service("minioFileSystem")
@RequiredArgsConstructor
public class MinioFileSystem implements FileSystemService {

	protected final Config conf;

	private MinioClient minioClient;

	@Override
	public void init() {
		log.info("[INIT_MINIO] {} | {}", conf.getMinioBucket(), conf.getMinioUrl());

		ConnectionPool connectionPool = new ConnectionPool(100, 300, TimeUnit.SECONDS);
		OkHttpClient client = new OkHttpClient.Builder().connectionPool(connectionPool).build();
		client.dispatcher().setMaxRequestsPerHost(100);
		this.minioClient = MinioClient.builder().httpClient(client).endpoint(conf.getMinioUrl()).credentials(conf.getMinioAccessKey(), conf.getMinioSecretKey()).build();
		minioClient.setTimeout(conf.getMinioConnectTimeout(), conf.getMinioWriteTimeout(), conf.getMinioReadTimeout());
	}

	@Override
	public XcnFileStatus status(String path) {
		try {
			StatObjectResponse response = getStatObject(path);
			return new XcnFileStatus(response.size(), false, response.lastModified().toInstant().toEpochMilli(), null);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public boolean exists(final String path) {
		try {
			getStatObject(path);
			return true;
		} catch (Exception e) {
			log.debug("FileNotFound: {}", path);
		}
		return false;
	}

	@Override
	public InputStream open(String path) throws Exception {
		long startTime = System.currentTimeMillis();
		InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(conf.getMinioBucket()).object(path).build());
		log.debug("[AT_OPEN] {} | {}", path, DateUtils.duration(startTime));
		return inputStream;
	}

	@Override
	public boolean delete(String path) {
		try {
			Map<String, String> header = new HashMap<>();
			header.put("x-minio-force-delete", "true");
			minioClient.removeObject(RemoveObjectArgs.builder().bucket(conf.getMinioBucket()).object(path).extraHeaders(header).build());
			return true;
		} catch (Exception e) {
			log.warn("[AT_DELETE] Error ", e);
		}
		return false;
	}

	@Override
	public void write(final String src, final String dst, final String fileName) throws Exception {
		try (FileInputStream in = new FileInputStream(src)) {
			write(dst, in, fileName);
		}
	}

	@Override
	public void writeText(String path, String text) throws Exception {
		try (ByteArrayInputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
			write(path, in, null);
		}
	}

	@Override
	public void write(String path, InputStream is, String fileName) throws Exception {
		long startTime = System.currentTimeMillis();
		try {
			minioClient.putObject(PutObjectArgs.builder().bucket(conf.getMinioBucket()).object(path).stream(is, -1, 10485760).build());
			log.debug("[AT_WRITE] {} | {} | {}", fileName, path, DateUtils.duration(startTime));
		} catch (Exception e) {
			log.error("", e);
			throw new IOException(e);
		}
	}

	private StatObjectResponse getStatObject(final String path) throws Exception {
		return minioClient.statObject(StatObjectArgs.builder().bucket(conf.getMinioBucket()).object(path).build());
	}
}
