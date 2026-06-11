package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.service.InfoLoaderService;
import com.xcurenet.logvault.loader.type.ServiceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class ServiceLoader {
	private final InfoLoaderService infoLoaderService;
	private final AtomicReference<Map<String, ServiceVO>> SERVICE_REF = new AtomicReference<>(Collections.emptyMap());

	public void load() {
		Map<String, ServiceVO> serviceMap = new HashMap<>();
		long version = infoLoaderService.getServiceVersion();
		List<ServiceVO> service = infoLoaderService.getService(version);
		for (ServiceVO item : service) {
			log.debug("INFO_LOAD | Service: {}", item);
			if (Common.isEmpty(item.getServiceCd()) || Common.isEquals(item.getUseYn(), "N") || Common.isEquals(item.getLoggingYn(), "N"))
				continue;

			serviceMap.put(item.getServiceCd(), item);
			log.info("INFO_LOAD | Rule Version : {} | Service | {} | {} | {}", version, item.getServiceCd(), item.getServiceName(), item.getCompanyAccountUseYn());
		}
		SERVICE_REF.set(serviceMap);
	}

	public boolean contains(final String svc) {
		return SERVICE_REF.get().containsKey(svc);
	}

	public ServiceVO get(final String svc) {
		if (Common.isEmpty(svc)) return null;
		return SERVICE_REF.get().get(svc);
	}

	public boolean isCompanyAccountUse(final String svc) {
		ServiceVO service = get(svc);
		return service != null && Common.isEquals(service.getCompanyAccountUseYn(), "Y");
	}
}
