package com.xcurenet.logvault.module.alert;

import com.xcurenet.logvault.loader.KeywordLoader;
import com.xcurenet.logvault.loader.PatternLoader;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.module.task.service.TaskMessageRepository;
import com.xcurenet.logvault.module.util.ActionType;
import com.xcurenet.logvault.opensearch.EmassDoc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService - 알림 발송 로직")
class AlertServiceTest {

	@Mock
	private TaskMessageRepository repository;
	@InjectMocks
	private AlertService alertService;

	@Nested
	@DisplayName("send(ScanData) 입력 검증")
	class InputValidation {
		@Test
		@DisplayName("null ScanData → 예외 없이 무시")
		void nullScanData() {
			assertDoesNotThrow(() -> alertService.send((ScanData) null));
			verifyNoInteractions(repository);
		}

		@Test
		@DisplayName("null EmassDoc → 예외 없이 무시")
		void nullEmassDoc() {
			ScanData sd = mock(ScanData.class);
			when(sd.getEmassDoc()).thenReturn(null);
			assertDoesNotThrow(() -> alertService.send(sd));
			verifyNoInteractions(repository);
		}
	}

	@Nested
	@DisplayName("send(EmassDoc) 서비스 조건")
	class ServiceCondition {
		@Test
		@DisplayName("null EmassDoc → 예외 없이 무시")
		void nullDoc() {
			assertDoesNotThrow(() -> alertService.send((EmassDoc) null));
		}

		@Test
		@DisplayName("svc3 != 'S' → 알림 미발송")
		void nonSendService() {
			EmassDoc doc = buildDoc("R"); // 수신 서비스
			assertDoesNotThrow(() -> alertService.send(doc));
			verifyNoInteractions(repository);
		}

		@Test
		@DisplayName("keyword + privacy 모두 0건 → 알림 미발송")
		void noDetection_noAlert() {
			EmassDoc doc = buildDoc("S");
			doc.setKeywordTotal(0);
			doc.setPrivacyTotal(0);

			assertDoesNotThrow(() -> alertService.send(doc));
			verifyNoInteractions(repository);
		}
	}

	private EmassDoc buildDoc(String svc3) {
		EmassDoc doc = new EmassDoc();
		doc.setMsgid("test-msg-id");
		doc.setAction(ActionType.ALLOW);
		doc.setTimestamp(new Date());
		doc.setCtime("20251104151028");

		EmassDoc.Service service = new EmassDoc.Service();
		service.setSvc("IABS");
		service.setSvc1("I");
		service.setSvc3(svc3);
		service.setSvc12("IAB");
		doc.setService(service);

		EmassDoc.KeywordInfo ki = new EmassDoc.KeywordInfo();
		ki.setExist(false);
		ki.setKeywords(Collections.emptyList());
		ki.setBody(null);
		ki.setAttach(null);
		ki.setAttachName(null);
		doc.setKeywordInfo(ki);

		return doc;
	}
}
