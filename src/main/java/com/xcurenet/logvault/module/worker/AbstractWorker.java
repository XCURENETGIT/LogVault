package com.xcurenet.logvault.module.worker;


import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.geo.GeoLocation;
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
import com.xcurenet.logvault.module.statics.ThroughputMetrics;
import com.xcurenet.logvault.module.task.service.TaskService;
import com.xcurenet.logvault.module.util.InsaManager;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StopWatch;

import java.io.File;
import java.util.List;
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
	protected final GeoLocation geoLocation;
	protected final IndexService indexService;
	protected final FilterService filterService;
	protected final AlertService alertService;
	protected final TaskService taskService;

	protected final ThroughputMetrics metrics;

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
		this.geoLocation = context.getBean(GeoLocation.class);
		this.filterService = context.getBean(FilterService.class);
		this.alertService = context.getBean(AlertService.class);
		this.taskService = context.getBean(TaskService.class);
		this.indexService = context.getBean(IndexService.class);
	}

	@Override
	public void run() {
		MDC.put("worker", Thread.currentThread().getName());

		while (run.get()) {
			ScanData data = poll();
			if (data == null) continue;
			try {
				inprogress.set(true);
				if (data.getFilePath() == null || !new File(data.getFilePath()).exists()) continue;

				data.setStopWatch(DateUtils.start());
				data.setStart(System.currentTimeMillis());

				process(data);                 // INFO 파일 파싱
				checkAttachments(data);        // 첨부파일 체크  (각 Worker 에서 파일 대기에 대한 기준을 재 정립, 첨부가 없으면 대기)
				parse(data);                   // 서비스별 추가 내용 파싱 (Error 발생 시 처음부터 재 처리)
				insaMapping(data);             // 인사 정보 연동 (Error 발생 시 처음부터 재 처리)

				boolean rs = filterService.filter(data); // 필터 처리     (Error 발생 시 처음부터 재 처리)
				if (!rs) { // 필터링 되는 파일의 경우 아래 내용은 처리하지 않음.
					analysisService.analyse(data); // 분석 기능     (Error 발생 시 무시)

					boolean success = false;
					int retryCnt = 1;
					while (retryCnt <= 3) {
						try {

							transToBody(data);      // 본문 전송     (Error 발생 시 해당 로직 3회 재처리 후 지속 에러 발생 시 처음부터 재 처리)
							transToAttach(data);    // 첨부파일 전송  (Error 발생 시 해당 로직 3회 재처리 후 지속 에러 발생 시 처음부터 재 처리)
							index(data);            // Elastic 색인 (Error 발생 시 해당 로직 3회 재처리 후 지속 에러 발생 시 처음부터 재 처리)
							alert(data);            // 이상행위 (룰) 탐지 시 알림 전송
							task(data);             // OCR 사용이면, OCR 처리
							success = true;
							break;
						} catch (final FileSendException | IndexerException e) {
							log.debug("ERROR | {} | {}", data.getMsgData().getMsgid(), e.getMessage(), e);
							retryCnt++;
							Common.sleep(2000);
						}
					}
					if (!success) return;            // 오류 상황 시 원본 데이터 삭제 금지. (재 처리시 필요함.)
				}
				clearService.clear(data);            // 처리 후 파일 삭제 (Error 발생 시 continue)
				logService.log(data);                // 완료 로그

				metrics.increment();
				LogVaultApplication.getMinuteBy1Count().incrementAndGet();  // 1분 통계 증가
				LogVaultApplication.getSecBy10Count().incrementAndGet();    // 10초 통계 증가
			} catch (final SkipFileException e) { // 첨부 파일이 늦게 들어오는 경우 대기 용도
				log.info("WAIT_SEC | {} | {} seconds until the file is available.\n", e.getMessage(), this.conf.getInterval() / 1000);
			} catch (final ProcessDataException | ParsingException e) {
				log.debug("{}", data.getFilePath(), e);
				// 기본 파싱이 되지 않는 다면 권한을 제거하여 재 처리 되는 오류를 방지한다.
				Common.removeAllPermissions(new File(data.getFilePath()));
			} catch (final Exception e) {
				log.warn("{} | {} | filePath={} err={}", ErrorCode.UNKNOWN_ERROR, ErrorCode.fromCode(ErrorCode.UNKNOWN_ERROR), data.getFilePath(), e.toString());
				Common.sleep(10000);
			} finally {
				MDC.remove("msgId");
				data.decrementCount(); // 처리 건수 감소
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
		} catch (final InterruptedException ignored) {
		}
		return null;
	}

	protected void process(ScanData data) throws ProcessDataException {
		StopWatch sw = DateUtils.start();

		MSGData msg = MSGParser.parse(data.getFilePath());
		data.setMsgData(msg);
		MDC.put("msgId", data.getMsgData().getMsgid());
		log.info("MG_START | {} | {} | ATT:{} | {} | {} | {}", msg.getSourceIp(), msg.getSvc(), msg.getAppFile().size(), msg.getSubject(), conf.getWmailPathSmall(data.getFilePath()), DateUtils.stop(sw));
	}

	protected void checkAttachments(ScanData data) throws SkipFileException {
		MSGData msg = data.getMsgData();
		if (msg.getHeader() != null) checkFiles(data, conf.getPath(msg.getHeader()), msg.getMsgid());
		if (msg.getMsgFile() != null) checkFiles(data, conf.getPath(msg.getMsgFile()), msg.getMsgid());
		if (msg.getAppFile() != null) {
			for (String appFile : msg.getAppFile()) {
				checkFiles(data, conf.getPath(appFile), msg.getMsgid());
			}
		}
	}

	protected abstract void parse(ScanData data) throws ParsingException;

	protected abstract void insaMapping(ScanData data);

	protected void transToAttach(ScanData data) throws FileSendException {
		MSGData msg = data.getMsgData();
		List<String> appPaths = msg.getAppFile();
		for (String src : appPaths) {
			if (src == null) continue;

			File file = new File(conf.getPath(src));
			if (!file.exists()) continue;

			String dest = conf.getDestPath(msg.getCtime(), msg.getMsgid(), new File(src).getName());
			try {
				StopWatch sw = DateUtils.start();
				fileSystem.write(file.getAbsolutePath(), dest, file.getName());
				log.info("ATT_SEND | {} ({}) | {}", conf.getDestPathSmall(dest), Common.convertFileSize(file.length()), DateUtils.stop(sw));
			} catch (Exception e) {
				throw ExFactory.ex(FileSendException::new, ErrorCode.FILE_SEND_FAIL, Map.of("src", file.getAbsolutePath(), "dst", dest), e);
			}
		}
	}

	protected void transToMsg(ScanData data) throws FileSendException {
		MSGData msg = data.getMsgData();
		if (msg.getInfoFilePath() == null) return;

		File file = new File(msg.getInfoFilePath());
		if (!file.exists()) return;

		String dest = conf.getDestPath(msg.getCtime(), msg.getMsgid(), msg.getInfoFilePath());
		try {
			StopWatch sw = DateUtils.start();
			fileSystem.write(file.getAbsolutePath(), dest, file.getName());
			log.info("MSG_SEND | {} ({}) | {}", conf.getDestPathSmall(dest), Common.convertFileSize(file.length()), DateUtils.stop(sw));
		} catch (Exception e) {
			throw ExFactory.ex(FileSendException::new, ErrorCode.FILE_MSG_SEND_FAIL, Map.of("src", file.getAbsolutePath(), "dest", dest), e);
		}
	}

	protected void transToBody(ScanData data) throws FileSendException {
		MSGData msg = data.getMsgData();
		if (msg.getMsgFile() == null) return;

		File file = new File(conf.getPath(msg.getMsgFile()));
		if (!file.exists()) return;

		String dest = conf.getDestPath(msg.getCtime(), msg.getMsgid(), msg.getMsgid() + ".body");
		try {
			StopWatch sw = DateUtils.start();
			fileSystem.write(file.getAbsolutePath(), dest, file.getName());
			log.info("BDY_SEND | {} ({}) | {}", conf.getDestPathSmall(dest), Common.convertFileSize(file.length()), DateUtils.stop(sw));
		} catch (Exception e) {
			throw ExFactory.ex(FileSendException::new, ErrorCode.FILE_BODY_SEND_FAIL, Map.of("src", file.getAbsolutePath(), "dest", dest), e);
		}
	}

	protected abstract void index(ScanData data) throws IndexerException;

	protected abstract void alert(ScanData data);

	protected abstract void task(ScanData data);

	/**
	 * 본문, 헤더, 첨부파일 파일이 없는 경우 최대 30분 대기
	 */
	protected void checkFiles(final ScanData data, final String path, final String msgId) throws SkipFileException {
		if (path != null && !new File(path).exists()) {
			if (System.currentTimeMillis() - data.getLastModified() < this.conf.getInterval()) {
				throw new SkipFileException(path);
			} else log.info("NOTFOUND | {} | {} seconds over", path, this.conf.getInterval() / 1000);
		}
	}
}
