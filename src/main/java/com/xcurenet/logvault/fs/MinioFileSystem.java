package com.xcurenet.logvault.fs;

import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.*;
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
		log.info("INIT_MINIO | {} | {}", conf.getMinioBucket(), conf.getMinioUrl());

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
		StopWatch sw = DateUtils.start();
		InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(conf.getMinioBucket()).object(path).build());
		log.debug("AT_OPEN | {} | {}", path, DateUtils.stop(sw));
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
			log.warn("AT_DELETE | Error ", e);
		}
		return false;
	}

	@Override
	public boolean deleteDirectory(String path) {
		try {
			Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder().bucket(conf.getMinioBucket()).prefix(path.endsWith("/") ? path : path + "/").recursive(true).build());
			for (Result<Item> result : results) {
				Item item = result.get();
				minioClient.removeObject(RemoveObjectArgs.builder().bucket(conf.getMinioBucket()).object(item.objectName()).build());
			}
			return true;
		} catch (Exception e) {
			log.warn("AT_DELETE | Error deleting directory {}", path, e);
			return false;
		}
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
		StopWatch sw = DateUtils.start();
		try {
			minioClient.putObject(PutObjectArgs.builder().bucket(conf.getMinioBucket()).object(path).stream(is, -1, 10485760).build());
			log.debug("AT_WRITE | {} | {} | {}", fileName, path, DateUtils.stop(sw));
		} catch (Exception e) {
			log.error("", e);
			throw new IOException(e);
		}
	}

	private StatObjectResponse getStatObject(final String path) throws Exception {
		return minioClient.statObject(StatObjectArgs.builder().bucket(conf.getMinioBucket()).object(path).build());
	}

	@Override
	public long getTotalSpace(final String src) {
		return 0L;
	}

	@Override
	public long getUsableSpace(String path) {
		return 0L;
	}

	@Override
	public long size(String path) {
		try {
			return getStatObject(path).size();
		} catch (Exception e) {
			log.warn("FILE_SIZE | {} | {}", path, e.getMessage());
			return 0L;
		}
	}
}
