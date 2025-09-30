package com.xcurenet.logvault.conf;

import java.util.HashMap;
import java.util.Map;

public class DefaultConfig {
	private DefaultConfig() {
		throw new IllegalStateException("Utility class");
	}

	public static Map<String, Object> getDefaultConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("file.system.type", "local");
		config.put("edc.attach.path", "/data01/attach/");
		config.put("decoder.split.dir", 100);
		config.put("scan.directory.scanning.waiting.sec", 3);
		config.put("data.path", "/users/las/msg/data");
		config.put("file.wait.time.sec", 1800);
		config.put("scan.dir.enable.wmail", true);

		config.put("scan.dir.wmail", "/users/las/msg/info/wmail");

		config.put("edc.body.snippet.size", 2000);
		config.put("edc.decompress.depth", 3);
		config.put("edc.extract.text.timeout", 5);
		config.put("ignore.extractor.ext", "gul,mpeg,mp3,asf,ra,rm,tiff,tif,png,gif,jpg,bmp,pcx,mid,wav,avi,pds");
		config.put("ocr.target.ext", "tiff,tif,png,gif,jpg,jpeg,bmp,pcx,dcx,jb2,jfif,jp2,jpc,j2k,pdf");
		config.put("edc.temp.path", "/tmp");
		config.put("edc.ramdisk.path", "/dev/shm/edc");
		config.put("edc.ramdisk.limit", 104857600);

		config.put("filter.http.response.content.type", "text/css,application/javascript,text/javascript,font/woff2");
		return config;
	}
}
