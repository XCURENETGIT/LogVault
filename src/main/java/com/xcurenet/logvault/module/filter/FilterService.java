package com.xcurenet.logvault.module.filter;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.exception.FilterException;
import com.xcurenet.logvault.module.ScanData;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class FilterService {
	public boolean filter(final ScanData data) throws FilterException {
		boolean rs;
		try {
			MSGData msg = data.getMsgData();
			rs = !Common.nvl(msg.getSvc()).startsWith("I");
			if (rs) {
				log.info("[FILT_SVC] {} | {}", msg.getMsgid(), msg.getSvc());
				return true;
			}
			rs = Common.isEquals(msg.getSvc(), "IUKU");
			if (rs) {
				log.info("[FILT_SVC] {} | {}", msg.getMsgid(), msg.getSvc());
				return true;
			}
		} catch (Exception e) {
			throw new FilterException(e);
		}
		return false;
	}
}
