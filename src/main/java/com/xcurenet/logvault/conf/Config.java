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

	private final static String ENC_KEYFILE = "/etc/xcnkey";

	public static String getEncryptKeyFile() {
		return ENC_KEYFILE;
	}

	@Value("${spring.profiles.active:prod}")
	private String activeProfile;

	@Value("${memory.disk.path:/dev/shm/file/}")
	private String memoryDiskPath;

	@Value("${xutf8.path:/users/logvault/lib/xutf_8}")
	private String xutf8Path;

	@Value("${xutf8.ext.path:/users/logvault/lib/xutf_8_ext}")
	private String xutf8ExtPath;

	@Value("${spring.opensearch.rest.uris}")
	private String opensearchRestUris;

	@Value("${file.system.type:local}") //파일 시스템 유형 - 운영중 설정 변경 불가 (재시작필요)
	private String fileSystemType;

	@Value("${attach.root:/data01/attach}") //첨부 저장경로 - 운영중 설정 변경 불가 (재시작필요)
	private String attachRoot;

	@Value("${index.root:/indexdata}") //색인 저장경로 - 운영중 설정 변경 불가 (재시작필요)
	private String indexRoot;

	@Value("${decoder.split.dir:100}") //디코더 디렉토리 분산 - 운영중 설정 변경 불가 (재시작필요)
	private int decoderSplitDir;

	@Value("${data.path:/users/las/msg/data}") //디코더 데이터 경로 - 운영중 설정 변경 불가 (재시작필요)
	private String dataPath;

	@Value("${data.backup.enable:false}") //데이터 백업 (첨부, 본문, OpenSearch Index)
	private boolean backupEnable;

	@Value("${data.backup.path:/data01/backup/}") //데이터 백업 (첨부, 본문, OpenSearch Index)
	private String backupPath;


	@Value("${scan.directory.scanning.waiting.sec:5}") //디코더 디렉토리 스캔 대기시간 - 운영중 설정 변경 불가 (재시작필요)
	private int scanDirectoryScanningWaitingSec;

	@Value("${file.wait.time.sec:600}") // 헤더, 본문, 첨부 파일 최대 대기 시간 - 운영중 설정 변경 불가 (재시작필요)
	private int fileWaitTime;

	@Value("${scan.dir.enable.wmail:true}") //WMAIL 경로 스캔 여부 - 운영중 설정 변경 불가 (재시작필요)
	private boolean enableWmail;

	@Value("${scan.dir.wmail:/users/las/msg/info/wmail}") //WMAIL 스캔 경로 - 운영중 설정 변경 불가 (재시작필요)
	private String dirWmail;

	@Value("${worker.size.wmail:5}") //WORKER 동시 처리 수 - 운영중 설정 변경 불가 (재시작필요)
	private int workerSizeWmail;

	@Value("${body.language.detect.size:2000}") //본문 국가탐지 시 본문 길이 제한
	private int bodyLanguageDetectSize;

	@Value("${decompress.depth:3}") //첨부파일 텍스트 추출 시 압축 파일 DEPTH
	private int decompressDepth;

	@Value("${extract.text.timeout.sec:60}") //첨부파일 텍스트 추출 TimeOut (초)
	private int extractTextTimeoutSec;

	@Value("${extract.image.enable:false}") //첨부파일의 이미지 추출 여부
	private boolean extractImage;

	@Value("${check.excel.hidden.sheet.enable:false}") //엑셀 숨김 시트 탐지 여부
	private boolean checkExcelHiddenSheet;

	@Value("${ocr.api.enable:true}") //OCR Rest API ENABLE
	private boolean ocrApiEnable;

	@Value("${ocr.embedded.enable:true}") //파일 내부의 이미지도 OCR 처리를 할것인지 유무
	private boolean ocrEmbeddedImageEnable;

	@Value("${ocr.api.url:http://10.200.10.49:62975/sdk/ocr}") //OCR Rest API URL
	private String ocrApiUrl;

	@Value("${ocr.api.local.cpu.url:http://10.100.20.209:8000/ocr}") //OCR LOCAL CPU Rest API URL
	private String ocrApiLocalCpuUrl;

	@Value("${ocr.api.local.url:http://10.100.20.209:8001/v1/chat/completions}") //OCR LOCAL Rest API URL
	private String ocrApiLocalUrl;

	@Value("${ocr.api.local.model:/models/allenai/olmOCR-2-7B-1025-FP8}") //OCR LOCAL Model
	private String ocrApiLocalModel;

	@Value("${ocr.api.key:SNOCR-834be64b6228442cac181eb08d84e56c}") //OCR Rest API KEY
	private String ocrApiKey;

	@Value("${ocr.api.timeout:600}") //OCR Rest API KEY
	private int ocrTimeoutSec;

	@Value("${ocr.target.ext:tiff,tif,png,gif,jpg,jpeg,bmp,pcx,dcx,jb2,jfif,jp2,jpc,j2k,pdf}") //OCR 대상 확장자
	private String ocrTargetExt;

	@Value("${ocr.limit.size:20485760}") //OCR 파일 사이즈 LIMIT (default 20MB)
	private int ocrLimitSize;

	public Set<String> getOcrTargetExt() {
		return new HashSet<>(Arrays.asList(ocrTargetExt.split(",")));
	}

	@Value("${ignore.extractor.ext:gul,mpeg,mp3,asf,ra,rm,tiff,tif,png,gif,jpg,bmp,pcx,mid,wav,avi,pds}")
	//텍스트 추출 예외 확장자
	private String ignoreExtractorExt;

	public Set<String> getIgnoreExtractorExt() {
		return new HashSet<>(Arrays.asList(ignoreExtractorExt.split(",")));
	}

	@Value("${ml.privacy.api.enable:true}") //ML Privacy Rest API ENABLE
	private boolean mlPrivacyApiEnable;

	@Value("${ml.privacy.api.url:http://127.0.0.1:8005/verify}") //ML Privacy Rest API URL
	private String mlPrivacyApiUrl;

	@Value("${ml.api.enable:true}") //ML Rest API ENABLE
	private boolean mlApiEnable;

	@Value("${ml.api.url:http://127.0.0.1:15000/api/data-analyze}") //ML Rest API URL
	private String mlApiUrl;

	@Value("${ml.api.timeout:600}") //ML Rest API TimeOut
	private int mlTimeoutSec;

	@Value("${ml.api.code.split.code_split_threshold:10}") //ML Rest API TimeOut
	private int mlCodeSplitThreshold;

	@Value("${ml.api.codeline.exist.threshold:3}") //ML Rest API TimeOut
	private int mlCodelineExistThreshold;

	@Value("${ml.api.detect.model.dir:251114_epochs1_ebed512-ch31}") //ML Rest API TimeOut
	private String mlDetectModelDir;

	@Value("${ml.api.text.limit:200000}") //ML TEXT LIMIT
	private int mlApiTextLimit;

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

	@Value("${spring.opensearch.index.room.name:aegis-room}")
	private String indexRoomName;

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

	@Value("${file.analysis.limit.size:104857600}") //파일 텍스트 추출 파일 사이즈 LIMIT (default 100MB)
	private int fileAnalysisLimitSize;

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

	@Value("${data.store.usage.limit:90}") //데이터 자동 삭제 임계치
	private int dataStoreUsageLimit;

	@Value("${data.store.usage:true}") //데이터 자동 삭제 임계치 사용여부
	private boolean dataStoreUsage;

	@Value("${filter.http.response.content.type:text/css,application/javascript,text/javascript,font/woff2}")
	//Response ContentType Filter
	private String filterResponseContentType;

	@Value("${filter.service.unknown:true}")
	private boolean filterServiceUnknown;

	@Value("${task.queue.workers.capacity:50}") //후 처리 큐 capacity
	private int taskQueueWorkersCapacity;

	@Value("${task.queue.workers.threads:10}") //후 처리 쓰레드 수
	private int taskQueueWorkersThreads;

	@Value("${task.queue.scheduler.fetch-size:50}") //후 처리 시 한번에 MariaDB에서 불러올 건수
	private int taskQueueSchedulerFetchSize;

	@Value("${thumbnail.retention.days:90}") //썸네일 이미지 보관 (MariaDB)
	private int thumbnailRetentionDays;

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

	public String getWmailPathSmall(final String path) {
		try {
			int idx = path.indexOf(getDirWmail());
			return (idx != -1) ? path.substring(idx + getDirWmail().length()) : path;
		} catch (Exception e) {
			return path;
		}
	}

	public String getDataPathSmall(final String path) {
		try {
			int idx = path.indexOf(getDataPath());
			return (idx != -1) ? path.substring(idx + getDataPath().length()) : path;
		} catch (Exception e) {
			return path;
		}
	}

	public String getDestPathSmall(final String path) {
		try {
			int idx = path.indexOf(getAttachRoot());
			return (idx != -1) ? path.substring(idx + getAttachRoot().length()) : path;
		} catch (Exception e) {
			return path;
		}
	}

	public static void main(String[] args) {
		String fileName = "20251104151028-01e13165-d8ef2415-57793-443-00-462358-DEBDA8FBC3951135ED28B45CFD0FAB8B-VI01.http-2.hdr";
		System.out.println(Common.makeFilepath("/users/las/msg/data", Long.toString(com.xcurenet.common.utils.Common.getSplitNum(fileName, 100)), fileName));
	}
}
