package com.xcurenet.logvault.conf;

import com.xcurenet.common.Constants;
import com.xcurenet.common.utils.Common;
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

	@Value("${file.system.type:local}") //파일 시스템 유형 - 운영중 설정 변경 불가 (재시작필요)
	private String fileSystemType;

	@Value("${attach.root:/data01/attach/}") //첨부 저장경로 - 운영중 설정 변경 불가 (재시작필요)
	private String attachRoot;

	@Value("${index.root:/indexdata/}") //색인 저장경로 - 운영중 설정 변경 불가 (재시작필요)
	private String indexRoot;

	@Value("${decoder.split.dir:100}") //디코더 디렉토리 분산 - 운영중 설정 변경 불가 (재시작필요)
	private int decoderSplitDir;

	@Value("${data.path:/users/las/msg/data}") //디코더 데이터 경로 - 운영중 설정 변경 불가 (재시작필요)
	private String dataPath;


	@Value("${scan.directory.scanning.waiting.sec:5}") //디코더 디렉토리 스캔 대기시간 - 운영중 설정 변경 불가 (재시작필요)
	private int scanDirectoryScanningWaitingSec;

	@Value("${file.wait.time.sec:1800}") // 헤더, 본문, 첨부 파일 최대 대기 시간 - 운영중 설정 변경 불가 (재시작필요)
	private int fileWaitTime;

	@Value("${scan.dir.enable.wmail:true}") //WMAIL 경로 스캔 여부 - 운영중 설정 변경 불가 (재시작필요)
	private boolean enableWmail;

	@Value("${scan.dir.wmail:/users/las/msg/info/wmail}") //WMAIL 스캔 경로 - 운영중 설정 변경 불가 (재시작필요)
	private String dirWmail;

	@Value("${worker.size.wmail:1}") //WORKER 동시 처리 수 - 운영중 설정 변경 불가 (재시작필요)
	private int workerSizeWmail;

	@Value("${body.language.detect.size:2000}") //본문 국가탐지 시 본문 길이 제한
	private int bodyLanguageDetectSize;

	@Value("${decompress.depth:2000}") //첨부파일 텍스트 추출 시 압축 파일 DEPTH
	private int decompressDepth;

	@Value("${extract.text.timeout:5}") //첨부파일 텍스트 추출 TimeOut
	private int extractTextTimeout;

	@Value("${ocr.target.ext:tiff,tif,png,gif,jpg,jpeg,bmp,pcx,dcx,jb2,jfif,jp2,jpc,j2k,pdf}") //OCR 대상 확장자
	private String ocrTargetExt;

	public Set<String> getOcrTargetExt() {
		return new HashSet<>(Arrays.asList(ocrTargetExt.split(",")));
	}

	@Value("${ignore.extractor.ext:gul,mpeg,mp3,asf,ra,rm,tiff,tif,png,gif,jpg,bmp,pcx,mid,wav,avi,pds}") //텍스트 추출 예외 확장자
	private String ignoreExtractorExt;

	public Set<String> getIgnoreExtractorExt() {
		return new HashSet<>(Arrays.asList(ignoreExtractorExt.split(",")));
	}

	@Value("${temp.path:/tmp}") //임시 저장 경로
	private String tempPath;

	@Value("${ramdisk.path:/dev/shm/edc}") //첨부파일 텍스트 추출 시 가장 빠른 디스크 경로
	private String ramdiskPath;

	@Value("${ramdisk.limit:104857600}") // 첨부파일 텍스트 추출 저장 여유 공간
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
	@Value("${encrypt.enable:true}") //본문, 첨부 암호화 저장 여부 - 운영중 설정 변경 불가 (재시작필요)
	private boolean encryptEnable;

	@Value("${encrypt.cipher:ARIA_256_CBC}") //암호화 알고리즘 - 운영중 설정 변경 불가 (재시작필요)
	private String encryptCipher;

	public Crypto.CIPHER getEncyptCipher() {
		return Crypto.CIPHER.getCipher(encryptCipher);
	}

	@Value("${encrypt.key:}")
	private String encryptKey;

	public byte[] getEncryptKey() {
		return Common.hexToBytes(encryptKey);
	}

	@Value("${encrypt.key.file:/etc/xcnkey}") //암호화 키 파일 경로 - 운영중 설정 변경 불가 (재시작필요)
	private String encryptKeyFile;

	@Value("${file.analysis.url:http://127.0.0.1:14545/api/text/path}") //파일 텍스트 추출 REST API
	private String fileAnalysisUrl;

	@Value("${privacy.analysis.url:http://127.0.0.1:14544/api/detectText.xcn}") //개인정보 추출  REST API
	private String privacyAnalysisUrl;

	@Value("${text.limit.length:10000000}") //텍스트 색인 시 최대 길이
	private int textLimitLength;

	@Value("${text.limit.token:100}") //텍스트 색인 시 한단어의 최대 길이
	private int textLimitToken;

	@Value("${data.store.term:365}") //데이터 보관 기간
	private int dataStoreTerm;

	@Value("${data.store.usage.limit:90}") //데이터 자동 삭제 임계치 사용여부 (사용:Y,미사용:N)
	private int dataStoreUsageLimit;

	@Value("${data.store.usage:N}") //데이터 자동 삭제 임계치
	private String dataStoreUsage;

	@Value("${filter.http.response.content.type:text/css,application/javascript,text/javascript,font/woff2}") //Response ContentType Filter
	private String filterResponseContentType;

	@Value("${task.queue.workers.capacity:100}") //후 처리 큐 capacity
	private int taskQueueWorkersCapacity;

	@Value("${task.queue.workers.threads:10}") //후 처리 쓰레드 수
	private int taskQueueWorkersThreads;

	@Value("${task.queue.scheduler.fetch-size:100}") //후 처리 시 한번에 MariaDB에서 불러올 건수
	private int taskQueueSchedulerFetchSize;

	public int getInterval() {
		return fileWaitTime * 1000;
	}

	public String getPath(final String fileName) {
		if (Common.isEmpty(fileName)) return null;
		return Common.makeFilepath(getDataPath(), Long.toString(Common.getSplitNum(fileName, getDecoderSplitDir())), fileName);
	}

	public String getDestPath(final DateTime ctime, final String msgId) {
		return Common.makeFilepath(getAttachRoot(), ctime.toString(Constants.YYYYMMDD), ctime.toString(Constants.HHMM_PATH), msgId);
	}

	public String getDestPath(final DateTime ctime, final String msgId, final String name) {
		return Common.makeFilepath(getDestPath(ctime, msgId), name);
	}
}
