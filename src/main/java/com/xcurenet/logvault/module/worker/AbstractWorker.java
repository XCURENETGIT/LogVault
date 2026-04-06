package com.xcurenet.logvault.module.worker;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.msg.MSGParser;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.LogVaultApplication;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.exception.*;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.alert.AlertService;
import com.xcurenet.logvault.module.analysis.AnalysisService;
import com.xcurenet.logvault.module.clear.ClearService;
import com.xcurenet.logvault.module.filter.FilterService;
import com.xcurenet.logvault.module.log.LogService;
import com.xcurenet.logvault.module.scanner.FileScanner;
import com.xcurenet.logvault.module.statics.StatService;
import com.xcurenet.logvault.module.statics.ThroughputMetrics;
import com.xcurenet.logvault.module.task.service.TaskService;
import com.xcurenet.logvault.module.util.InsaManager;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StopWatch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
@Log4j2
public abstract class AbstractWorker implements Runnable {

	private final PriorityBlockingQueue<ScanData> queue;
	private final AtomicBoolean run;
	private final AtomicBoolean inprogress = new AtomicBoolean(false);

	protected final Config conf;
	protected final InsaManager insaManager;
	protected final FileProcessor fileSystem;
	protected final AnalysisService analysisService;
	protected final ClearService clearService;
	protected final LogService logService;
	protected final IndexService indexService;
	protected final FilterService filterService;
	protected final AlertService alertService;
	protected final TaskService taskService;
	protected final ThroughputMetrics metrics;
	protected final StatService statService;

	protected AbstractWorker(final ApplicationContext context, final PriorityBlockingQueue<ScanData> queue, final AtomicBoolean run) {
		this.queue = queue;
		this.run = run;
		this.conf = context.getBean(Config.class);
		this.insaManager = context.getBean(InsaManager.class);
		this.fileSystem = context.getBean(FileProcessor.class);
		this.analysisService = context.getBean(AnalysisService.class);
		this.logService = context.getBean(LogService.class);
		this.clearService = context.getBean(ClearService.class);
		this.metrics = context.getBean(ThroughputMetrics.class);
		this.filterService = context.getBean(FilterService.class);
		this.alertService = context.getBean(AlertService.class);
		this.taskService = context.getBean(TaskService.class);
		this.indexService = context.getBean(IndexService.class);
		this.statService = context.getBean(StatService.class);
	}

	@Override
	public void run() {
		MDC.put("worker", Thread.currentThread().getName());

		while (run.get()) {
			ScanData data = poll();
			if (data == null) continue;

			try {
				inprogress.set(true);

				Path filePath = data.getFilePath();
				if (!Files.exists(filePath)) continue;

				data.setStopWatch(DateUtils.start());
				data.setStart(System.currentTimeMillis());

				process(data);
				checkAttachments(data);
				parse(data);

				boolean filtered = filterService.filter(data);
				if (!filtered) {
					analysisService.analyse(data);

					boolean success = false;
					int retryCnt = 1;
					while (retryCnt <= 3) {
						try {
							insaMapping(data);              // 인사정보 연동
							roomId(data);                   // 생성형AI 룸 번호 생성 (서비스 IAS_사용자 아이디 OR SRC_IP  BASE64)
							transToInfo(data);              // INFO 정보 전송
							transToBody(data);              // 본문 전송 (프롬프트 내용)
							transToAttach(data);            // 첨부파일 전송
							transToAttachEmbedded(data);    // 첨부내 객체 전송
							index(data);                    // 색인
							statService.processEvent(data); // 통계생성
							success = true;
							break;
						} catch (final FileSendException | IndexerException | InsaMappingException e) {
							log.debug("ERROR | {} | {}", data.getMsgData().getMsgid(), e.getMessage(), e);
							retryCnt++;
							Common.sleep(2000);
						}
					}
					if (!success) {
						log.error("{} | FILEPATH:{} | 3회 재시도 실패로 NOK 이동", ErrorCode.UNKNOWN_ERROR.toString(), data.getFilePath());
						Common.moveNok(data.getFilePath(), conf.getNokRoot(), conf.getDataPath(), conf.getDecoderSplitDir());
						continue; // NOK로 이동 후 다음 작업 계속 처리
					}

					while (retryCnt <= 3) {
						try {
							boolean postProcessingTarget = task(data); //OCR 및 분석 관련 TASK
							if (!postProcessingTarget) { // 후 처리 대상이 아닌 경우만 즉각 알림 전송
								alert(data);
							}
							break;
						} catch (final Exception e) { // 성공 여부 관계없이 알림 전송, OCR 및 분석 후처리의 경우는 별도로 처리
							log.warn("TASK_ERROR | {}", e.getMessage(), e);
							retryCnt++;
							Common.sleep(2000);
						}
					}

					metrics.increment();
					LogVaultApplication.getMinuteBy1Count().incrementAndGet();
					LogVaultApplication.getSecBy10Count().incrementAndGet();
				}

				clearService.clear(data);
				logService.log(data);
			} catch (final SkipFileException e) {
				log.info("WAIT_SEC | {} | {} seconds\n", e.getMessage(), this.conf.getInterval() / 1000);
				Common.sleep(3000);
			} catch (final ProcessDataException | ParsingException | FilterException e) { // 파싱오류, 필터링 오류 등의 문제 발생 시 MSG 파일의 문제로 간주 하고 NOK 디렉토리로 이동
				log.debug("{}", data.getFilePath(), e);
				Common.moveNok(data.getFilePath(), conf.getNokRoot(), conf.getDataPath(), conf.getDecoderSplitDir());
			} catch (final Exception e) { // 이 경우도 MSG 파일의 문제로 간주 하고 NOK 디렉토리로 이동
				log.warn("{} | FILEPATH:{} ERR:{}", ErrorCode.UNKNOWN_ERROR.toString(), data.getFilePath(), e.toString(), e);
				Common.moveNok(data.getFilePath(), conf.getNokRoot(), conf.getDataPath(), conf.getDecoderSplitDir());
			} finally {
				MDC.remove("msgId");
				data.decrementCount();
				FileScanner.removeFromQueue(data.getFilePath().toAbsolutePath().toString());
				inprogress.set(false);
			}
		}
	}

	public boolean getProgress() {
		return inprogress.get();
	}

	private ScanData poll() {
		try {
			return queue.poll(1, TimeUnit.SECONDS);
		} catch (InterruptedException ignored) {
		}
		return null;
	}

	protected void process(ScanData data) throws ProcessDataException {
		StopWatch sw = DateUtils.start();

		MSGData msg = MSGParser.parse(data);
		data.setMsgData(msg);

		MDC.put("msgId", msg.getMsgid());
		log.info("MG_START | {} | {} | ATT:{} | {} | {} | {}", msg.getSourceIp(), msg.getSvc(), msg.getAppFile().size(), msg.getSubject(), conf.getWmailPathSmall(data.getFilePath().toAbsolutePath().toString()), DateUtils.stop(sw));
	}

	protected void checkAttachments(ScanData data) throws SkipFileException {
		MSGData msg = data.getMsgData();
		if (msg.getMsgFile() != null) checkFiles(data, conf.getPath(msg.getMsgFile()));

		if (msg.getAppFile() != null) {
			for (String appFile : msg.getAppFile()) {
				checkFiles(data, conf.getPath(appFile));
			}
		}
	}

	protected abstract void parse(ScanData data) throws ParsingException;

	protected abstract void insaMapping(ScanData data);

	protected abstract void roomId(ScanData data);

	/**
	 * 첨부파일 전송 (NIO 기반)
	 */
	protected void transToAttach(ScanData data) throws FileSendException {
		MSGData msg = data.getMsgData();

		for (String src : msg.getAppFile()) {
			if (src == null) continue;

			Path srcPath = Paths.get(conf.getPath(src));
			if (!Files.exists(srcPath)) continue;

			String destStr = conf.getDestPath(msg.getCtime(), msg.getMsgid(), srcPath.getFileName().toString());
			Path destPath = Paths.get(destStr);
			try {
				if (fileSystem.exists(destStr)) {
					log.info("ATT_SKIP | {} already exists. Skipping copy.", conf.getDestPathSmall(destStr));
					continue;
				}

				StopWatch sw = DateUtils.start();
				fileSystem.write(srcPath.toString(), destPath.toString(), srcPath.getFileName().toString());
				long size = Files.size(srcPath);
				log.info("ATT_SEND | {} ({}) | {}", conf.getDestPathSmall(destStr), Common.convertFileSize(size), DateUtils.stop(sw));
			} catch (Exception e) {
				throw ExFactory.ex(FileSendException::new, ErrorCode.FILE_SEND_FAIL, Map.of("src", srcPath.toString(), "dst", destPath.toString()), e);
			}
		}
	}

	//첨부에 포함된 객체
	protected void transToAttachEmbedded(ScanData data) throws FileSendException {
		MSGData msg = data.getMsgData();
		for (String src : msg.getEmbeddedFile()) {
			if (src == null) continue;

			Path srcPath = Paths.get(src); //embeddedFile의 경우 src 패스가 전체 경로로 된다.
			if (!Files.exists(srcPath)) continue;

			String destStr = conf.getDestPath(msg.getCtime(), msg.getMsgid(), srcPath.getFileName().toString());
			Path destPath = Paths.get(destStr);
			try {
				if (fileSystem.exists(destStr)) {
					log.info("EMB_SKIP | {} already exists. Skipping copy.", conf.getDestPathSmall(destStr));
					continue;
				}

				StopWatch sw = DateUtils.start();
				fileSystem.write(srcPath.toString(), destPath.toString(), srcPath.getFileName().toString());
				long size = Files.size(srcPath);
				log.info("EMB_SEND | {} ({}) | {}", conf.getDestPathSmall(destStr), Common.convertFileSize(size), DateUtils.stop(sw));
			} catch (Exception e) {
				throw ExFactory.ex(FileSendException::new, ErrorCode.EMBED_SEND_FAIL, Map.of("src", srcPath.toString(), "dst", destPath.toString()), e);
			}
		}
	}


	protected void transToInfo(ScanData data) throws FileSendException {
		MSGData msg = data.getMsgData();
		if (msg.getInfoFilePath() == null) return;

		Path srcPath = Paths.get(msg.getInfoFilePath());
		if (!Files.exists(srcPath)) return;

		String destStr = conf.getDestPath(msg.getCtime(), msg.getMsgid(), srcPath.getFileName().toString());
		Path destPath = Paths.get(destStr);
		try {
			if (fileSystem.exists(destStr)) {
				log.info("MSG_SKIP | {} already exists. Skipping copy.", conf.getDestPathSmall(destStr));
				return;
			}

			StopWatch sw = DateUtils.start();
			fileSystem.write(srcPath.toString(), destPath.toString(), srcPath.getFileName().toString());
			long size = Files.size(srcPath);
			log.info("MSG_SEND | {} ({}) | {}", conf.getDestPathSmall(destStr), Common.convertFileSize(size), DateUtils.stop(sw));
		} catch (Exception e) {
			throw ExFactory.ex(FileSendException::new, ErrorCode.FILE_MSG_SEND_FAIL, Map.of("src", srcPath.toString(), "dest", destPath.toString()), e);
		}
	}

	/**
	 * 본문 전송
	 */
	protected void transToBody(ScanData data) throws FileSendException {
		MSGData msg = data.getMsgData();
		if (msg.getMsgFile() == null) return;

		Path srcPath = Paths.get(conf.getPath(msg.getMsgFile()));
		if (!Files.exists(srcPath)) return;

		String destStr = conf.getDestPath(msg.getCtime(), msg.getMsgid(), msg.getMsgFile());
		Path destPath = Paths.get(destStr);
		try {
			if (fileSystem.exists(destStr)) {
				log.info("BDY_SKIP | {} already exists. Skipping copy.", conf.getDestPathSmall(destStr));
				return;
			}

			StopWatch sw = DateUtils.start();
			fileSystem.write(srcPath.toString(), destPath.toString(), srcPath.getFileName().toString());
			long size = Files.size(srcPath);
			log.info("BDY_SEND | {} ({}) | {}", conf.getDestPathSmall(destStr), Common.convertFileSize(size), DateUtils.stop(sw));
		} catch (Exception e) {
			throw ExFactory.ex(FileSendException::new, ErrorCode.FILE_BODY_SEND_FAIL, Map.of("src", srcPath.toString(), "dest", destPath.toString()), e);
		}
	}

	protected abstract void index(ScanData data) throws IndexerException;

	protected abstract void alert(ScanData data);

	protected abstract boolean task(ScanData data);


	/**
	 * 파일이 존재하지 않으면 최대 interval 시간 동안 대기
	 */
	protected void checkFiles(final ScanData data, final String pathStr) throws SkipFileException {
		Path path = Paths.get(pathStr);
		if (Files.notExists(path)) {
			if (System.currentTimeMillis() - data.getLastModified() < this.conf.getInterval()) {
				throw new SkipFileException(pathStr);
			} else {
				log.info("NOTFOUND | {} | {} seconds over", pathStr, this.conf.getInterval() / 1000);
			}
		}
	}
}
