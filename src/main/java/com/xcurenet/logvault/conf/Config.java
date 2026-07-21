package com.xcurenet.logvault.conf;

import com.xcurenet.common.Constants;
import com.xcurenet.common.utils.Common;
import com.xcurenet.crypto.Crypto;
import com.xcurenet.logvault.module.util.IdentificationMode;
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

	@Value("${memory.disk.path:/dev/shm/file/}")
	private String memoryDiskPath;

	@Value("${ramdisk.limit:104857600}") // 첨부파일 텍스트 추출 저장 여유 공간
	private long ramdiskLimit;

	@Value("${xutf8.path:/users/logvault/lib/xutf_8}")
	private String xutf8Path;

	@Value("${xutf8.ext.path:/users/logvault/lib/xutf_8_ext}")
	private String xutf8ExtPath;

	@Value("${spring.opensearch.rest.uris}")
	private String opensearchRestUris;


	@Value("${user.identification.mode:IP}") //인사정보 탐지 기준 (IP, PORT)
	private IdentificationMode userIdentificationMode;

	@Value("${file.system.type:local}") //파일 시스템 유형 - 운영중 설정 변경 불가 (재시작필요)
	private String fileSystemType;

	@Value("${attach.root:/data01/attach}") //첨부 저장경로 - 운영중 설정 변경 불가 (재시작필요)
	private String attachRoot;

	@Value("${index.root:/indexdata}") //색인 저장경로 - 운영중 설정 변경 불가 (재시작필요)
	private String indexRoot;

	@Value("${nok.root:/data01/nok}") //MSG 파일의 파싱 등의 오류 발생 시 백업 디렉토리
	private String nokRoot;

	@Value("${nok.retention.days:90}") //MSG 파일의 파싱 등의 오류 발생 시 백업 디렉토리 삭제일 (default 90일)
	private int nokRetentionDays;

	@Value("${decoder.split.dir:100}") //디코더 디렉토리 분산 - 운영중 설정 변경 불가 (재시작필요)
	private int decoderSplitDir;

	@Value("${data.path:/users/las/msg/data}") //디코더 데이터 경로 - 운영중 설정 변경 불가 (재시작필요)
	private String dataPath;

	@Value("${data.path:/run/ngpii}") //차단 데이터 경로 - 운영중 설정 변경 불가 (재시작필요)
	private String blockDataPath;

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

	@Value("${worker.size.wmail:1}") //WORKER 동시 처리 수 - 운영중 설정 변경 불가 (재시작필요)
	private int workerSizeWmail;

	@Value("${scan.dir.enable.wmail.block:true}") //WMAIL 차단 경로 스캔 여부 - 운영중 설정 변경 불가 (재시작필요)
	private boolean enableWmailBlock;

	@Value("${scan.dir.wmail.block:/run/ngpii}") //WMAIL 차단 스캔 경로 - 운영중 설정 변경 불가 (재시작필요)
	private String dirWmailBlock;

    @Value("${worker.size.wmail.block:1}") //WORKER 차단 동시 처리 수 - 운영중 설정 변경 불가 (재시작필요)
	private int workerSizeWmailBlock;

    @Value("${scan.dir.enable.shadow:true}") //SHADOW AI 경로 스캔 여부 - 운영중 설정 변경 불가 (재시작필요)
    private boolean enableShadowAi;

    @Value("${scan.dir.shadow:/users/xcn-nginx/logs/shadowai}") //SHADOW AI 스캔 경로 - 운영중 설정 변경 불가 (재시작필요)
    private String dirShadowAi;

    @Value("${worker.size.shadow:1}") //WORKER SHADOW AI 동시 처리 수 - 운영중 설정 변경 불가 (재시작필요)
    private int workerSizeShadowAi;

	@Value("${decompress.depth:3}") //첨부파일 텍스트 추출 시 압축 파일 탐지 DEPTH
	private int decompressDepth;

	@Value("${file.analysis.limit.size:104857600}") //파일 텍스트 추출 파일 사이즈 LIMIT (default 100MB)
	private int fileAnalysisLimitSize;

	@Value("${text.limit.length:10000000}") //텍스트 색인 시 최대 길이
	private int textLimitLength;

	@Value("${text.limit.token:100}") //텍스트 색인 시 한단어의 최대 길이
	private int textLimitToken;

	@Value("${extract.text.timeout.sec:60}") //첨부파일 텍스트 추출 TimeOut (초)
	private int extractTextTimeoutSec;

	@Value("${extract.image.enable:true}") //첨부파일의 이미지 추출 여부
	private boolean extractImage;

	@Value("${check.excel.hidden.sheet.enable:true}") //엑셀 숨김 시트 탐지 여부
	private boolean checkExcelHiddenSheet;

	@Value("${ocr.api.enable:true}") //OCR Rest API ENABLE
	private boolean ocrApiEnable;

	@Value("${ocr.embedded.enable:true}") //파일 내부의 이미지도 OCR 처리를 할것인지 유무
	private boolean ocrEmbeddedImageEnable;

	@Value("${ocr.api.type:LC}") //OCR TYPE (SY: Synap OCR, LG: Local GPU, LC:Local CPU)
	private String ocrApiType;

	@Value("${ocr.api.host:xgenai-manager}") //OCR Rest API Host
	private String ocrApiHost;

	@Value("${ocr.api.port:8001}") //OCR Rest API PORT
	private String ocrApiPort;

	@Value("${ocr.api.local.gpu.model:/models/allenai/olmOCR-2-7B-1025-FP8}") //OCR LOCAL Model
	private String ocrApiLocalGpuModel;

	@Value("${ocr.api.key:SNOCR-834be64b6228442cac181eb08d84e56c}") //OCR Rest API KEY
	private String ocrApiKey;

	@Value("${ocr.api.timeout:600}") //OCR Rest API KEY
	private int ocrTimeoutSec;

	@Value("${ocr.target.ext:tiff,tif,png,gif,jpg,jpeg,bmp,pcx,dcx,jb2,jfif,jp2,jpc,j2k,pdf}") //OCR 대상 확장자
	private Set<String> ocrTargetExt;

	@Value("${image.similarity.target.ext:jpg,png,webp,bmp,gif,tiff,heic,heif}")
	private Set<String> imageSimilarityTargetExt;

	/**
	 * OCR 처리 모듈에 따라 지원 확장자가 다르다.
	 *
	 * @return 지원 가능한 확장자
	 */
	public Set<String> getOcrTargetExt() {
		if (Common.isEquals(ocrApiType, "LC"))
			return new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "bmp", "tiff", "webp", "gif", "pdf"));
		return ocrTargetExt;
	}

	@Value("${ocr.limit.size:20485760}") //OCR 파일 사이즈 LIMIT (default 20MB)
	private int ocrLimitSize;

	@Value("${ml.privacy.api.url:http://xgenai-manager:8005/pii/detect}") //ML Privacy Rest API URL
	private String mlPrivacyApiUrl;

	@Value("${ml.privacy.grpc.host:xgenai-manager}") //ML Privacy GRPC HOST
	private String mlPrivacyGrpcHost;

	@Value("${ml.privacy.grpc.port:50055}") //ML Privacy GRPC PORT
	private int mlPrivacyGrpcPort;

	@Value("${ml.privacy.grpc.tls.enable:false}") //ML Privacy GRPC TLS ENABLE
	private boolean mlPrivacyGrpcTlsEnable;

	@Value("${ml.privacy.grpc.deadline.ms:20000}") //ML Privacy GRPC Deadline (ms)
	private int mlPrivacyGrpcDeadlineMs;

	@Value("${ml.privacy.grpc.retry.max-attempts:3}") //ML Privacy GRPC Retry Count
	private int mlPrivacyGrpcRetryMaxAttempts;

	@Value("${ml.privacy.grpc.retry.backoff.ms:200}") //ML Privacy GRPC Retry Backoff (ms)
	private int mlPrivacyGrpcRetryBackoffMs;

	@Value("${ml.privacy.grpc.circuit.failure-threshold:5}") //ML Privacy GRPC Circuit Breaker Failure Threshold
	private int mlPrivacyGrpcCircuitFailureThreshold;

	@Value("${ml.privacy.grpc.circuit.open.ms:30000}") //ML Privacy GRPC Circuit Breaker Open Duration (ms)
	private int mlPrivacyGrpcCircuitOpenMs;

	@Value("${privacy.vietnam.enable:Y}") //VN_* 개인정보 탐지 여부 (Y/N)
	private String privacyVietnamEnable;

	@Value("${gs.enable:Y}") //GS 모드 여부 (Y/N)
	private String gsEnable;

	public boolean isVietnamPrivacyEnabled() {
		return !isYnDisabled(privacyVietnamEnable) && isYnDisabled(gsEnable);
	}

	private boolean isYnDisabled(String value) {
		return Common.isEquals(Common.nvl(value).trim().toUpperCase(), "N");
	}

	@Value("${guardrail.api.url:http://xgenai-manager:9901/guardrail}") //guardrail Rest API URL
	private String guardRailApiUrl;

	@Value("${guardrail.limit.rate:70}") //guardrail limit rate
	private int guardRailLimitRate;

	@Value("${guardrail.limit.length:20}") //guardrail limit length
	private int guardRailLimitLength;

	@Value("${ml.api.enable:true}") //ML Rest API ENABLE
	private boolean mlApiEnable;

	@Value("${ml.api.url:http://xgenai-manager:15000/api/data-analyze}") //ML Rest API URL
	private String mlApiUrl;

	@Value("${ml.similarity.url:http://xgenai-manager:18000/api/search}") //SIMILARITY API URL
	private String similarityUrl;

	@Value("${ml.similarity.key:xcn-rag}") //SIMILARITY KEY
	private String similarityKey;

    @Value("${image.similarity.enable:true}") // ML Image similarity API ENABLE
    private boolean imageSimilarityEnable;

	@Value("${image.similarity.api.host:http://xgenai-manager:15001}")
	private String imageSimilarityApiHost;

	@Value("${image.similarity.api.detect:/detect}")
	private String imageSimilarityApiDetect;

	@Value("${ml.api.timeout:600}") //ML Rest API TimeOut
	private int mlTimeoutSec;

	@Value("${ml.api.code.split.code_split_threshold:10}") //ML Rest API TimeOut
	private int mlCodeSplitThreshold;

	@Value("${ml.api.codeline.exist.threshold:3}") //ML Rest API TimeOut
	private int mlCodelineExistThreshold;

	@Value("${ml.api.detect.model.dir:251114_epochs1_ebed512-ch31}") //ML Rest API TimeOut
	private String mlDetectModelDir;

	@Value("${ml.api.text.limit:1000000}") //ML TEXT LIMIT
	private int mlApiTextLimit;

	@Value("${spring.minio.url:http://xgenai-manager:9000}")
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

	@Value("${spring.opensearch.index.shadow-ai.name:shadow-ai-}")
	private String indexShadowAiName;

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

	@Value("${data.store.usage:true}") //데이터 자동 삭제 임계치 사용여부
	private boolean dataStoreUsage;

	@Value("${data.store.term:365}") //데이터 보관 기간
	private int dataStoreTerm;

	@Value("${data.store.usage.limit:90}") //데이터 자동 삭제 임계치
	private int dataStoreUsageLimit;

	@Value("${filter.http.response.content.type:text/css,application/javascript,text/javascript,font/woff2}")
	//Response ContentType Filter
	private String filterResponseContentType;

	@Value("${filter.service.unknown:true}") // 서비스 타입이 unknown인 경우 필터링 처리
	private boolean filterServiceUnknown;

	@Value("${task.queue.workers.capacity:50}") //후 처리 큐 capacity
	private int taskQueueWorkersCapacity;

	@Value("${task.queue.ocr.threads:4}") //OCR 후 처리 동시 실행 수
	private int taskQueueOcrThreads;

	@Value("${task.queue.ml.threads:8}") //ML 후 처리 동시 실행 수
	private int taskQueueMlThreads;

	@Value("${task.queue.alert.threads:2}") //Alert 후 처리 동시 실행 수
	private int taskQueueAlertThreads;

	@Value("${task.queue.scheduler.fetch-size:50}") //후 처리 시 한번에 MariaDB에서 불러올 건수
	private int taskQueueSchedulerFetchSize;

	@Value("${task.queue.scheduler.interval-ms:2000}") //후 처리 큐 스캔 주기 (ms)
	private long taskQueueSchedulerIntervalMs;

	@Value("${thumbnail.retention.days:90}") //썸네일 이미지 보관 (MariaDB)
	private int thumbnailRetentionDays;

	@Value("${decrypt.file.path:/users/aegis_ai/rule/}") // 차단/허용 정책 파일 경로
	private String decryptFilePath;

	@Value("${decrypt.file.rule.json:rules.json}") // 차단/허용 정책 파일명
	private String decryptFileRuleJson;

	public int getInterval() {
		return fileWaitTime * 1000;
	}

	public String getPath(final String fileName, final boolean blocked) {
		if (Common.isEmpty(fileName)) return null;
		return Common.makeFilepath(blocked? getBlockDataPath() : getDataPath(), Long.toString(Common.getSplitNum(fileName, getDecoderSplitDir())), fileName);
	}

	public String getDestPath(final DateTime ctime, final String msgId) {
		return Common.makeFilepath(getAttachRoot(), ctime.toString(Constants.YYYYMMDD), ctime.toString(Constants.HHMM_PATH), msgId);
	}

	public String getDestPath(final DateTime ctime, final String msgId, final String name) {
		return Common.makeFilepath(getDestPath(ctime, msgId), name);
	}

	public String getWmailPathSmall(final String path) {
		return getSmallPath(path, getDirWmail(), getDirWmailBlock());
	}

	public String getWmailPathSmall(final String path, final boolean blocked) {
		return blocked ? getSmallPath(path, getDirWmailBlock(), getDirWmail()) : getSmallPath(path, getDirWmail(), getDirWmailBlock());
	}

	public String getDataPathSmall(final String path) {
		return getSmallPath(path, getDataPath(), getBlockDataPath());
	}

	public String getDataPathSmall(final String path, final boolean blocked) {
		return blocked ? getSmallPath(path, getBlockDataPath(), getDataPath()) : getSmallPath(path, getDataPath(), getBlockDataPath());
	}

	private String getSmallPath(final String path, final String primaryRoot, final String fallbackRoot) {
		try {
			String smallPath = removeRoot(path, primaryRoot);
			if (smallPath != null) return smallPath;

			smallPath = removeRoot(path, fallbackRoot);
			return smallPath != null ? smallPath : path;
		} catch (Exception e) {
			return path;
		}
	}

	private String removeRoot(final String path, final String root) {
		if (Common.isEmpty(path) || Common.isEmpty(root)) return null;
		int idx = path.indexOf(root);
		return (idx != -1) ? path.substring(idx + root.length()) : null;
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
