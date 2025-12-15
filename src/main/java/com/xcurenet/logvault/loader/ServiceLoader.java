package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.ServiceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class ServiceLoader {
	private final InfoLoaderMapper mapper;
	private final AtomicReference<Set<String>> SERVICE_REF = new AtomicReference<>();

	public void load() {
		Set<String> serviceSet = new HashSet<>();
		List<ServiceVO> service = mapper.getService();
		for (ServiceVO item : service) {
			if (Common.isEmpty(item.getServiceCd())) continue;
			serviceSet.add(item.getServiceCd());
			log.info("INFO_LOAD | Service | {} | {}", item.getServiceCd(), item.getServiceName());
		}
		SERVICE_REF.set(serviceSet);
	}

	public boolean contains(final String svc) {
		return SERVICE_REF.get().contains(svc);
	}
}
