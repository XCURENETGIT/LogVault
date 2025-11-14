package com.xcurenet.common.log;

import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Setter;

import java.util.Map;

@Setter
public class MdcValueDenyFilter extends Filter<ILoggingEvent> {
	private String mdcKey;
	private String denyValue;
	private boolean denyIfMissing = false;

	@Override
	public FilterReply decide(ILoggingEvent event) {
		if (!isStarted()) return FilterReply.NEUTRAL;

		Map<String, String> m = event.getMDCPropertyMap();
		String v = (m != null ? m.get(mdcKey) : null);

		if (v == null) {
			return denyIfMissing ? FilterReply.DENY : FilterReply.NEUTRAL;
		}
		if (denyValue != null && denyValue.equals(v)) {
			return FilterReply.DENY;
		}
		return FilterReply.NEUTRAL;
	}

	@Override
	public void start() {
		if (mdcKey != null && !mdcKey.isEmpty()) {
			super.start();
		}
	}
}