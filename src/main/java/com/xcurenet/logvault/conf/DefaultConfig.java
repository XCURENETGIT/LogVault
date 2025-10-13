package com.xcurenet.logvault.conf;

import java.util.HashMap;
import java.util.Map;

public class DefaultConfig {
	private DefaultConfig() {
		throw new IllegalStateException("Utility class");
	}

	public static Map<String, Object> getDefaultConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("decoder.split.dir", 100); //운영중 설정 변경 불가 (재시작필요)
		config.put("file.system.type", "local"); //운영중 설정 변경 불가 (재시작필요)
		config.put("attach.path", "/data01/attach/"); //운영중 설정 변경 불가 (재시작필요)
		config.put("data.path", "/users/las/msg/data"); //운영중 설정 변경 불가 (재시작필요)

		config.put("scan.directory.scanning.waiting.sec", 3); //운영중 설정 변경 불가 (재시작필요)
		config.put("scan.dir.enable.wmail", true); //운영중 설정 변경 불가 (재시작필요)
		config.put("scan.dir.wmail", "/users/las/msg/info/wmail"); //운영중 설정 변경 불가 (재시작필요)
		config.put("worker.size.wmail", 1); //운영중 설정 변경 불가 (재시작필요)
		config.put("file.wait.time.sec", 1800); //운영중 설정 변경 불가 (재시작필요)

		config.put("encrypt.enable", true); //운영중 설정 변경 불가 (재시작필요)
		config.put("encrypt.cipher", "ARIA_256_CBC"); //운영중 설정 변경 불가 (재시작필요)
		config.put("encrypt.key.file", "/etc/xcnkey"); //운영중 설정 변경 불가 (재시작필요)

		config.put("body.language.detect.size", 2000);
		config.put("decompress.depth", 3);
		config.put("extract.text.timeout", 5);
		config.put("ignore.extractor.ext", "gul,mpeg,mp3,asf,ra,rm,tiff,tif,png,gif,jpg,bmp,pcx,mid,wav,avi,pds");
		config.put("ocr.target.ext", "tiff,tif,png,gif,jpg,jpeg,bmp,pcx,dcx,jb2,jfif,jp2,jpc,j2k,pdf");
		config.put("temp.path", "/tmp");
		config.put("ramdisk.path", "/dev/shm/edc");
		config.put("ramdisk.limit", 104857600);

		config.put("file.analysis.url", "http://127.0.0.1:14545/api/text/path");
		config.put("privacy.analysis.url", "http://127.0.0.1:14544/api/detectText.xcn");
		config.put("text.limit.length", 10000000); // 색인 텍스트 최대 길이 제한
		config.put("text.limit.token", 100); //하나의 토큰 최대 길이 제한

		config.put("filter.http.response.content.type", "text/css,application/javascript,text/javascript,font/woff2");
		return config;
	}
}
