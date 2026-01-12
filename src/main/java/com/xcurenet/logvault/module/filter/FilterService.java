package com.xcurenet.logvault.module.filter;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.exception.FilterException;
import com.xcurenet.logvault.exception.IndexerException;
import com.xcurenet.logvault.loader.ServiceLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class FilterService {
	private final Config config;
	private final ServiceLoader serviceLoader;

	public boolean filter(final ScanData data) throws FilterException {
		MSGData msg = data.getMsgData();
		boolean rs;
		try {
			if (!isLoggingService(data.getEmassDoc())) {
				log.info("FILT_SVC | {}", msg.getSvc());
				return true;
			}

			if (msg.getSvc() == null) return true;

			rs = !Common.nvl(msg.getSvc()).startsWith("I");
			if (rs) {
				log.info("FILT_SVC | {}", msg.getSvc());
				return true;
			}

			if (config.isFilterServiceUnknown()) {
				rs = Common.isEquals(msg.getSvc(), "IUKU");
				if (rs) {
					log.info("FILT_SVC | {}", msg.getSvc());
					return true;
				}
			}

			//본문, 첨부 둘다 없는 경우는 필터 대상
			if (Common.isEmpty(msg.getMsgFile()) && msg.getAppFile().isEmpty()) {
				log.info("FILT_SVC | body is empty, attach is empty : {}", msg.getSvc());
				return true;
			}
		} catch (Exception e) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_SAVE_FAIL, Map.of("svc", msg.getSvc()), e);
		}
		return false;
	}

	private boolean isLoggingService(final EmassDoc doc) {
		if (doc == null || doc.getService() == null) return false;
		return serviceLoader.contains(doc.getService().getSvc12());
	}
}
