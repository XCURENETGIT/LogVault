package com.xcurenet.logvault.module.scanner;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.logvault.LogVaultApplication;
import com.xcurenet.logvault.module.ScanData;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Log4j2
public class Scanner extends DirectoryWalker<File> implements Runnable {

	private final File startDirectory;
	private final PriorityBlockingQueue<ScanData> scanQueue;
	private final AtomicBoolean run;
	private final AtomicInteger scannerCount = new AtomicInteger();
	private final int scanningWaitingSec;

	private static final long DELETE_TMP_DELAY = 24 * 60 * 60 * 1000; //1일이 경과한 파일 삭제

	public Scanner(final String dir, PriorityBlockingQueue<ScanData> scanQueue, AtomicBoolean run, int scanningWaitingSec) {
		super(null, 2);
		this.scanQueue = scanQueue;
		this.run = run;
		this.startDirectory = new File(dir);
		this.scanningWaitingSec = scanningWaitingSec;
	}

	@Override
	public void run() {
		Thread.currentThread().setName(startDirectory.getName() + "_scan");
		while (run.get()) {
			try {
				walk(startDirectory, null);
			} catch (IOException e) {
				log.error("Error while scanning directory: {}", startDirectory, e);
			}
			log.debug("[WAITING] {} seconds for directory scanning: {}, ScannerCount : {}, QueueSize : {}", scanningWaitingSec, startDirectory, scannerCount.get(), scanQueue.size());
			for (int i = 0; i < scanningWaitingSec && run.get(); i++) {
				CommonUtil.sleep(1000); // 1초 대기
			}

			while (run.get() && scannerCount.get() > 0) {
				log.debug("directory : {}, ScannerCount : {}, QueueSize : {}", startDirectory, scannerCount.get(), scanQueue.size());
				CommonUtil.sleep(1000);
			}
		}
	}

	@Override
	protected void handleFile(File file, int depth, final Collection<File> results) {
		if (file.length() > 0 && filePermission(file) && !file.isHidden()) { // 파일 권한 검사 7xx or not
			try {
				addQueue(getData(file));
			} catch (Exception e) {
				log.error("info file parsing error : {}", file.getAbsolutePath(), e);
			}
		} else {
			// 파일 생성이 24시간 지났으면서 권한이 7xx가 아닌 파일 삭제 (권한 검사는 앞에서 진행)
			if (CommonUtil.diffTime(file.lastModified()) > DELETE_TMP_DELAY) {
				log.info("Time delay and not permission: {}", file.getAbsolutePath());
				if (CommonUtil.isWindow()) return;
				try {
					Files.delete(file.toPath());
				} catch (IOException e) {
					log.error("Error while deleting file: {}", file.getAbsolutePath(), e);
				}
			}
		}
	}

	@Override
	protected boolean handleDirectory(File directory, int depth, final Collection<File> results) {
		return handleDir(directory);
	}

	@Override
	protected boolean handleIsCancelled(final File file, final int depth, final Collection<File> results) {
		return !run.get();
	}

	@Override
	protected void handleCancelled(final File startDirectory, final Collection<File> results, final CancelException cancel) {
	}

	private ScanData getData(final File file) throws Exception {
		return new ScanData(file, scannerCount);
	}

	private boolean filePermission(final File file) {
		if (CommonUtil.isWindow()) return true;
		return file.canRead() && file.canWrite() && file.canExecute();
	}

	private void addQueue(final ScanData data) {
		while (run.get() && scanQueue.size() >= LogVaultApplication.QUEUE_CAPACITY) {
			CommonUtil.sleep(1000);
		}
		if (!run.get()) return;

		scanQueue.add(data);
		data.incrementCount();
		log.debug("Queue : {}", data.getFilePath());
	}

	private boolean handleDir(final File directory) {
		final File[] listFiles = directory.listFiles();
		if (listFiles == null) return false;
		if (listFiles.length > 0) return true;
		// 인자로 받은 디렉터리 경로가 파일의 from path와 동일하면 true 반환, 24시간 사용하지 않은 폴더가 되어 삭제되지 않도록 하기 위해서
		if (startDirectory.getAbsolutePath().equals(directory.getAbsolutePath())) return true;

		// 24시간이 지나고 디렉터리 밑에 파일이 없는 경우에 삭제 후 다시 생성
		if (CommonUtil.diffTime(directory.lastModified()) > DELETE_TMP_DELAY) {
			log.debug("Delete and recreate the Directory that has no files while specific hours: {}", directory.getAbsolutePath());
			if (directory.delete()) directory.deleteOnExit();
			try {
				FileUtils.forceMkdir(directory);
			} catch (IOException e) {
				log.error("Error while creating directory: {}", directory, e);
			}
			return false;
		}
		return true;
	}
}