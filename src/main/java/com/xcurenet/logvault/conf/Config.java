package com.xcurenet.logvault.conf;

import com.xcurenet.common.Constants;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.crypto.Crypto;
import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration class to manage application-level properties and settings.
 * This class is annotated with {@code @Component} to mark it as a Spring-managed component and
 * {@code @RefreshScope} to allow dynamic property updates from external sources without restarting the application.
 * Utilizes the {@code @Value} annotation to inject property values from configuration files or environment variables.
 * The {@code @Data} annotation is used to generate boilerplate code such as getters, setters, and other utility methods.
 * <p>
 * The Config class includes properties related to:
 * - File system type configuration and path settings
 * - Directory scanning and file handling parameters
 * - Service enablement for various protocols and functionalities
 * - External service integration settings, such as MinIO and Kafka
 * <p>
 * Each property in this class defaults to a specific value if not explicitly configured, ensuring the safe operation of the application in predefined environments.
 * <p>
 * Constants:
 * - {@code PID_FILE}: Fixed location of the application PID file.
 * <p>
 * Key Areas:
 * 1. File system settings: Includes paths for attachments, data storage, and miscellaneous file handling configurations.
 * 2. Scanning directory enablement: Flags to activate or deactivate scanning for specific services or protocols.
 * 3. MinIO integration: Contains connection and authentication details to interact with MinIO storage.
 * 4. Kafka integration: Configures Kafka producer settings, including server URLs, serializers, and retry mechanisms.
 * 5. Domain configurations: Provides information related to internal/external domain settings for processing.
 */
@Data
@Component
@RefreshScope
public class Config {

	public final static String PID_FILE = "./bin/application.pid";

	@Value("${spring.profiles.active:prod}")
	private String activeProfile;

	@Value("${file.system.type:local}")
	private String fileSystemType;

	@Value("${attach.path:/data01/attach/}")
	private String edcAttachPath;

	@Value("${decoder.split.dir:100}")
	private int decoderSplitDir;

	@Value("${scan.directory.scanning.waiting.sec:5}")
	private int scanDirectoryScanningWaitingSec;

	@Value("${data.path:/users/las/msg/data}")
	private String dataPath;

	@Value("${file.wait.time.sec:1800}")
	private int fileWaitTime;

	@Value("${scan.dir.enable.wmail:true}")
	private boolean enableWmail;

	@Value("${scan.dir.wmail:/users/las/msg/info/wmail}")
	private String dirWmail;

	@Value("${worker.size.wmail:1}")
	private int workerSizeWmail;

	@Value("${body.language.detect.size:2000}")
	private int bodyLanguageDetectSize;

	@Value("${decompress.depth:2000}")
	private int decompressDepth;

	@Value("${extract.text.timeout:5}")
	private int extractTextTimeout;

	@Value("${ocr.target.ext:tiff,tif,png,gif,jpg,jpeg,bmp,pcx,dcx,jb2,jfif,jp2,jpc,j2k,pdf}")
	private String ocrTargetExt;

	public Set<String> getOcrTargetExt() {
		return new HashSet<>(Arrays.asList(ocrTargetExt.split(",")));
	}

	@Value("${ignore.extractor.ext:gul,mpeg,mp3,asf,ra,rm,tiff,tif,png,gif,jpg,bmp,pcx,mid,wav,avi,pds}")
	private String ignoreExtractorExt;

	public Set<String> getIgnoreExtractorExt() {
		return new HashSet<>(Arrays.asList(ignoreExtractorExt.split(",")));
	}

	@Value("${temp.path:/tmp}")
	private String tempPath;

	@Value("${ramdisk.path:/dev/shm/edc}")
	private String ramdiskPath;

	@Value("${ramdisk.limit:104857600}")
	private long ramdiskLimit;

	@Value("${spring.minio.url:http://127.0.0.1:9000}")
	private String minioUrl;

	@Value("${spring.minio.bucket:emass}")
	private String minioBucket;

	@Value("${spring.minio.accessKey:minioadmin}")
	private String minioAccessKey;

	@Value("${spring.minio.secretKey:minioadmin}")
	private String minioSecretKey;

	@Value("${spring.minio.connectTimeout:10000}")
	private int minioConnectTimeout;

	@Value("${spring.minio.writeTimeout:60000}")
	private int minioWriteTimeout;

	@Value("${spring.minio.readTimeout:10000}")
	private int minioReadTimeout;

	@Value("${spring.opensearch.index.name:emass-}")
	private String indexName;

	//	암호화 관련 설정
	@Value("${encrypt.enable:true}")
	private boolean encryptEnable;

	@Value("${encrypt.cipher:ARIA_256_CBC}")
	private String encryptCipher;

	public Crypto.CIPHER getEncyptCipher() {
		return Crypto.CIPHER.getCipher(encryptCipher);
	}

	@Value("${encrypt.key:}")
	private String encryptKey;

	public byte[] getEncryptKey() {
		return CommonUtil.hexToBytes(encryptKey);
	}

	@Value("${encrypt.key.file:/etc/xcnkey}")
	private String encryptKeyFile;

	@Value("${file.analysis.url:http://127.0.0.1:14545/api/text/path}")
	private String fileAnalysisUrl;

	@Value("${privacy.analysis.url:http://127.0.0.1:14544/api/detectText.xcn}")
	private String privacyAnalysisUrl;

	@Value("${text.limit.length:10000000}")
	private int textLimitLength;

	@Value("${text.limit.token:100}")
	private int textLimitToken;

	public int getInterval() {
		return fileWaitTime * 1000;
	}

	public String getPath(final String fileName) {
		if (CommonUtil.isEmpty(fileName)) return null;
		return CommonUtil.makeFilepath(getDataPath(), Long.toString(CommonUtil.getSplitNum(fileName, getDecoderSplitDir())), fileName);
	}

	public String getDestPath(final DateTime ctime, final String msgId) {
		return CommonUtil.makeFilepath(getEdcAttachPath(), ctime.toString(Constants.YYYYMMDD), ctime.toString(Constants.HHMM_PATH), msgId);
	}

	public String getDestPath(final DateTime ctime, final String msgId, final String name) {
		return CommonUtil.makeFilepath(getDestPath(ctime, msgId), name);
	}
}
