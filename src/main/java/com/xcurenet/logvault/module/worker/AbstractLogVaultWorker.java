package com.xcurenet.logvault.module.worker;


import com.xcurenet.common.geo.GeoLocation;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.msg.MSGParser;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.LogVaultApplication;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.exception.*;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.analysis.AnalysisService;
import com.xcurenet.logvault.module.clear.ClearService;
import com.xcurenet.logvault.module.filter.FilterService;
import com.xcurenet.logvault.module.log.LogService;
import com.xcurenet.logvault.module.statics.ThroughputMetrics;
import com.xcurenet.logvault.module.util.InsaManager;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.opensearch.data.client.orhlc.OpenSearchRestTemplate;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
@Log4j2
public abstract class AbstractLogVaultWorker implements Runnable {
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
	protected final OpenSearchRestTemplate template;
	protected final FilterService filterService;

	protected final ThroughputMetrics metrics;

	protected AbstractLogVaultWorker(final ApplicationContext context, final PriorityBlockingQueue<ScanData> queue, final AtomicBoolean run) {
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
		this.template = context.getBean(OpenSearchRestTemplate.class);
	}

	@Override
	public void run() {
		while (run.get()) {
			ScanData data = poll();
			if (data == null) continue;

			try {
				log.debug("worker data : {}", data);
				inprogress.set(true);
				if (data.getFilePath() == null || !new File(data.getFilePath()).exists()) continue;

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
							success = true;
							break;
						} catch (final FileSendException | IndexerException e) {
							log.error("[ERROR] {} | {}", data.getMsgData().getMsgid(), e.getMessage(), e);
							retryCnt++;
							CommonUtil.sleep(2000);
						}
					}
					if (!success) return;                // 오류 상황 시 원본 데이터 삭제 금지. (재 처리시 필요함.)
				}
				clearService.clear(data);            // 처리 후 파일 삭제 (Error 발생 시 continue)
				logService.log(data);                  // 완료 로그

				metrics.increment();
				LogVaultApplication.getMinuteBy1Count().incrementAndGet();  // 1분 통계 증가
				LogVaultApplication.getSecBy10Count().incrementAndGet();    // 10초 통계 증가
			} catch (final SkipFileException e) { // 첨부 파일이 늦게 들어오는 경우 대기 용도
				log.info("[WAIT_SEC] {} | {} seconds until the file is available.", e.getMessage(), this.conf.getInterval() / 1000);
			} catch (final ProcessDataException | ParsingException | InsaMappingException e) {
				log.warn("{}", data.getFilePath(), e);
			} catch (final Exception e) {
				log.warn("{}", data.getFilePath(), e);
				CommonUtil.sleep(10000);
			} finally {
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
		long startTime = System.currentTimeMillis();
		MSGData msg = MSGParser.parse(data.getFilePath());
		msg.setHeaderPath(conf.getPath(msg.getHeader()));
		msg.setMsgFilePath(conf.getPath(msg.getMsgFile()));
		for (String appFile : msg.getAppFile()) {
			msg.getAppFilePath().add(conf.getPath(appFile));
		}
		for (String pcFile : msg.getPcFile()) {
			msg.getPcFilePath().add(conf.getPath(pcFile));
		}
		data.setMsgData(msg);

		log.info("[MG_START] {} | {} | {}", msg.getMsgid(), msg.getInfoFilePath(), DateUtils.duration(startTime));
		log.debug("[MG_START] {}", msg);
	}

	protected void checkAttachments(ScanData data) throws SkipFileException {
		MSGData msg = data.getMsgData();
		if (msg.getHeaderPath() != null) checkFiles(data, msg.getHeaderPath(), msg.getMsgid());
		if (msg.getMsgFilePath() != null) checkFiles(data, msg.getMsgFilePath(), msg.getMsgid());
		if (msg.getAppFile() != null) {
			for (String appFilePath : msg.getAppFilePath()) {
				checkFiles(data, appFilePath, msg.getMsgid());
			}
		}
	}

	protected abstract void parse(ScanData data) throws ParsingException;

	protected abstract void insaMapping(ScanData data) throws InsaMappingException;

	protected void transToAttach(ScanData data) throws FileSendException {
		MSGData msg = data.getMsgData();
		List<String> files = msg.getAppFilePath();
		for (String path : files) {
			File file = new File(path);
			if (file.exists()) {
				try {
					long startTime = System.currentTimeMillis();
					String dest = conf.getDestPath(msg.getCtime(), msg.getMsgid(), file.getName());
					fileSystem.write(path, dest, file.getName());
					log.info("[ATT_SEND] {} | {} ({}) | {} | {}", msg.getMsgid(), path, CommonUtil.convertFileSize(file.length()), dest, DateUtils.duration(startTime));
				} catch (Exception e) {
					throw new FileSendException(e);
				}
			}
		}
	}

	protected void transToBody(ScanData data) throws FileSendException {
		try {
			MSGData msg = data.getMsgData();
			if (msg.getMsgFilePath() == null) return;

			File file = new File(msg.getMsgFilePath());
			if (!file.exists()) return;

			long startTime = System.currentTimeMillis();
			String dest = conf.getDestPath(msg.getCtime(), msg.getMsgid(), file.getName());
			fileSystem.write(msg.getMsgFilePath(), dest, file.getName());

			log.info("[BDY_SEND] {} | {} ({}) | {}", msg.getMsgid(), dest, CommonUtil.convertFileSize(file.length()), DateUtils.duration(startTime));
		} catch (Exception e) {
			throw new FileSendException(e);
		}
	}

	protected abstract void index(ScanData data) throws IndexerException;

	/**
	 * 본문, 헤더, 첨부파일 파일이 없는 경우 최대 30분 대기
	 */
	protected void checkFiles(final ScanData data, final String path, final String msgId) throws SkipFileException {
		if (path != null && !new File(path).exists()) {
			if (System.currentTimeMillis() - data.getLastModified() < this.conf.getInterval()) {
				throw new SkipFileException(msgId + " | " + path);
			} else log.info("[NOTFOUND] {} | {} | {} seconds over", msgId, path, this.conf.getInterval() / 1000);
		}
	}
}
