package com.xcurenet.logvault.module.filter;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.exception.FilterException;
import com.xcurenet.logvault.exception.IndexerException;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class FilterService {
	public boolean filter(final ScanData data) throws FilterException {
		MSGData msg = data.getMsgData();
		boolean rs;
		try {
			rs = !Common.nvl(msg.getSvc()).startsWith("I");
			if (rs) {
				log.info("FILT_SVC | {}", msg.getSvc());
				return true;
			}
			rs = Common.isEquals(msg.getSvc(), "IUKU");
			if (rs) {
				log.info("FILT_SVC | {}", msg.getSvc());
				return true;
			}
		} catch (Exception e) {
			throw ExFactory.ex(IndexerException::new, ErrorCode.INDEX_SAVE_FAIL, Map.of("svc", msg.getSvc()), e);
		}
		return false;
	}
}
