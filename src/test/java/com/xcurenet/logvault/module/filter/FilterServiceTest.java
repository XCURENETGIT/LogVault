package com.xcurenet.logvault.module.filter;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.exception.FilterException;
import com.xcurenet.logvault.loader.ServiceLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FilterService - 메시지 필터링 로직")
class FilterServiceTest {

	@Mock
	private Config config;
	@Mock
	private ServiceLoader serviceLoader;
	@InjectMocks
	private FilterService filterService;

	@Mock
	private ScanData scanData;
	private MSGData msgData;
	private EmassDoc emassDoc;

	@BeforeEach
	void setUp() {
		msgData = new MSGData();
		emassDoc = new EmassDoc();
		lenient().when(scanData.getMsgData()).thenReturn(msgData);
		lenient().when(scanData.getEmassDoc()).thenReturn(emassDoc);
	}

	private void setupService(String svc) {
		msgData.setSvc(svc);
		EmassDoc.Service service = new EmassDoc.Service();
		service.setSvc(svc);
		if (svc != null && svc.length() >= 2) {
			service.setSvc1(String.valueOf(svc.charAt(0)));
			service.setSvc12(svc.substring(0, Math.min(3, svc.length())));
		}
		emassDoc.setService(service);
	}

	@Nested
	@DisplayName("[1] BLOCK 액션")
	class BlockAction {
		@Test
		@DisplayName("BLOCK → 필터링 안함 (false), 파이프라인 진행")
		void block_shouldNotFilter() throws FilterException {
			msgData.setAction("BLOCK");
			assertFalse(filterService.filter(scanData));
		}

		@Test
		@DisplayName("BLOCK은 서비스 체크 없이 바로 통과")
		void block_skipsServiceCheck() throws FilterException {
			msgData.setAction("BLOCK");
			msgData.setSvc(null);
			assertFalse(filterService.filter(scanData));
			verifyNoInteractions(serviceLoader);
		}
	}

	@Nested
	@DisplayName("[2] 로깅 대상 서비스")
	class LoggingService {
		@Test
		@DisplayName("ServiceLoader에 미등록 서비스 → 필터링 (true)")
		void unregisteredService_shouldFilter() throws FilterException {
			msgData.setAction("ALLOW");
			setupService("IABS");
			when(serviceLoader.contains("IAB")).thenReturn(false);
			assertTrue(filterService.filter(scanData));
		}
	}

	@Nested
	@DisplayName("[3] SVC null")
	class SvcNull {
		@Test
		@DisplayName("SVC가 null → 필터링 (true)")
		void nullSvc_shouldFilter() throws FilterException {
			msgData.setAction("ALLOW");
			msgData.setSvc(null);
			EmassDoc.Service service = new EmassDoc.Service();
			service.setSvc12("IA");
			emassDoc.setService(service);
			when(serviceLoader.contains("IA")).thenReturn(true);
			assertTrue(filterService.filter(scanData));
		}
	}

	@Nested
	@DisplayName("[4] SVC 접두사 'I' 검증")
	class SvcPrefix {
		@Test
		@DisplayName("'I'로 시작하지 않는 SVC → 필터링 (true)")
		void nonIPrefix_shouldFilter() throws FilterException {
			msgData.setAction("ALLOW");
			setupService("EABS");
			when(serviceLoader.contains("EAB")).thenReturn(true);
			assertTrue(filterService.filter(scanData));
		}

		@Test
		@DisplayName("'I'로 시작하는 SVC → 필터링 안함")
		void iPrefix_shouldNotFilter() throws FilterException {
			msgData.setAction("ALLOW");
			setupService("IABS");
			msgData.setMsgFile("body.html");
			when(serviceLoader.contains("IAB")).thenReturn(true);
			assertFalse(filterService.filter(scanData));
		}
	}

	@Nested
	@DisplayName("[5] Unknown 서비스 (IUKU)")
	class UnknownService {
		@Test
		@DisplayName("IUKU + filterServiceUnknown=true → 필터링 (true)")
		void iuku_withFilterEnabled_shouldFilter() throws FilterException {
			msgData.setAction("ALLOW");
			setupService("IUKU");
			when(serviceLoader.contains("IUK")).thenReturn(true);
			when(config.isFilterServiceUnknown()).thenReturn(true);
			assertTrue(filterService.filter(scanData));
		}

		@Test
		@DisplayName("IUKU + filterServiceUnknown=false → 필터링 안함")
		void iuku_withFilterDisabled_shouldNotFilter() throws FilterException {
			msgData.setAction("ALLOW");
			setupService("IUKU");
			msgData.setMsgFile("body.html");
			when(serviceLoader.contains("IUK")).thenReturn(true);
			when(config.isFilterServiceUnknown()).thenReturn(false);
			assertFalse(filterService.filter(scanData));
		}
	}

	@Nested
	@DisplayName("[6] 본문·첨부 없음")
	class EmptyContent {
		@Test
		@DisplayName("본문 없음 + 첨부 없음 → 필터링 (true)")
		void noBodyNoAttach_shouldFilter() throws FilterException {
			msgData.setAction("ALLOW");
			setupService("IABS");
			msgData.setMsgFile(null);
			msgData.setAppFile(new ArrayList<>());
			when(serviceLoader.contains("IAB")).thenReturn(true);
			when(config.isFilterServiceUnknown()).thenReturn(false);
			assertTrue(filterService.filter(scanData));
		}

		@Test
		@DisplayName("본문 있음 + 첨부 없음 → 통과 (false)")
		void bodyOnly_shouldPass() throws FilterException {
			msgData.setAction("ALLOW");
			setupService("IABS");
			msgData.setMsgFile("body.html");
			msgData.setAppFile(new ArrayList<>());
			when(serviceLoader.contains("IAB")).thenReturn(true);
			when(config.isFilterServiceUnknown()).thenReturn(false);
			assertFalse(filterService.filter(scanData));
		}

		@Test
		@DisplayName("본문 없음 + 첨부 있음 → 통과 (false)")
		void attachOnly_shouldPass() throws FilterException {
			msgData.setAction("ALLOW");
			setupService("IABS");
			msgData.setMsgFile(null);
			msgData.setAppFile(new ArrayList<>(Collections.singletonList("file.zip")));
			when(serviceLoader.contains("IAB")).thenReturn(true);
			when(config.isFilterServiceUnknown()).thenReturn(false);
			assertFalse(filterService.filter(scanData));
		}
	}
}
