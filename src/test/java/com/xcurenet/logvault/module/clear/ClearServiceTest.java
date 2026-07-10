package com.xcurenet.logvault.module.clear;

import com.xcurenet.common.msg.MSGData;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClearService - 파일 정리 로직")
class ClearServiceTest {

	@Mock
	private Config conf;
	@InjectMocks
	private ClearService clearService;

	@TempDir
	Path tempDir;

	@Nested
	@DisplayName("clear() - 전체 정리 흐름")
	class Clear {

		@Test
		@DisplayName("MSG, 본문, 헤더, 첨부 파일 모두 삭제")
		void allFiles_shouldBeDeleted() throws Exception {
			Path msgFile = Files.createFile(tempDir.resolve("test.msg"));
			Path bodyFile = Files.createFile(tempDir.resolve("body.html"));
			Path headerFile = Files.createFile(tempDir.resolve("test.hdr"));
			Path attachFile = Files.createFile(tempDir.resolve("attach.zip"));

			MSGData msgData = new MSGData();
			msgData.setMsgFile("body.html");
			msgData.setHeader("test.hdr");
			msgData.setAppFile(new ArrayList<>(List.of("attach.zip")));
			msgData.setPcFile(new ArrayList<>());
			msgData.setEmbeddedFile(new ArrayList<>());

			when(conf.getPath("body.html", false)).thenReturn(bodyFile.toString());
			when(conf.getPath("test.hdr", false)).thenReturn(headerFile.toString());
			when(conf.getPath("attach.zip", false)).thenReturn(attachFile.toString());

			ScanData scanData = mock(ScanData.class);
			when(scanData.getFilePath()).thenReturn(msgFile);
			when(scanData.getMsgData()).thenReturn(msgData);

			clearService.clear(scanData);

			assertFalse(Files.exists(msgFile), "MSG 파일 삭제됨");
			assertFalse(Files.exists(bodyFile), "본문 파일 삭제됨");
			assertFalse(Files.exists(headerFile), "헤더 파일 삭제됨");
			assertFalse(Files.exists(attachFile), "첨부 파일 삭제됨");
		}

		@Test
		@DisplayName("본문/헤더 null일 때 NPE 없이 정상 처리")
		void nullBodyAndHeader_shouldNotThrow() throws Exception {
			Path msgFile = Files.createFile(tempDir.resolve("test.msg"));

			MSGData msgData = new MSGData();
			msgData.setMsgFile(null);
			msgData.setHeader(null);
			msgData.setAppFile(new ArrayList<>());
			msgData.setPcFile(new ArrayList<>());
			msgData.setEmbeddedFile(new ArrayList<>());

			ScanData scanData = mock(ScanData.class);
			when(scanData.getFilePath()).thenReturn(msgFile);
			when(scanData.getMsgData()).thenReturn(msgData);

			assertDoesNotThrow(() -> clearService.clear(scanData));
		}

		@Test
		@DisplayName("pcFile도 삭제 대상")
		void pcFile_shouldBeDeleted() throws Exception {
			Path msgFile = Files.createFile(tempDir.resolve("test.msg"));
			Path pcFile = Files.createFile(tempDir.resolve("pcfile.docx"));

			MSGData msgData = new MSGData();
			msgData.setMsgFile(null);
			msgData.setHeader(null);
			msgData.setAppFile(new ArrayList<>());
			msgData.setPcFile(new ArrayList<>(List.of("pcfile.docx")));
			msgData.setEmbeddedFile(new ArrayList<>());

			ScanData scanData = mock(ScanData.class);
			when(scanData.getFilePath()).thenReturn(msgFile);
			when(scanData.getMsgData()).thenReturn(msgData);
			when(conf.getPath("pcfile.docx", false)).thenReturn(pcFile.toString());

			clearService.clear(scanData);

			assertFalse(Files.exists(pcFile), "pcFile 삭제됨");
		}

		@Test
		@DisplayName("embeddedFile은 전체 경로로 삭제")
		void embeddedFile_deletedByFullPath() throws Exception {
			Path msgFile = Files.createFile(tempDir.resolve("test.msg"));
			Path embeddedFile = Files.createFile(tempDir.resolve("embedded_img.png"));

			MSGData msgData = new MSGData();
			msgData.setMsgFile(null);
			msgData.setHeader(null);
			msgData.setAppFile(new ArrayList<>());
			msgData.setPcFile(new ArrayList<>());
			msgData.setEmbeddedFile(new ArrayList<>(List.of(embeddedFile.toString())));

			ScanData scanData = mock(ScanData.class);
			when(scanData.getFilePath()).thenReturn(msgFile);
			when(scanData.getMsgData()).thenReturn(msgData);

			clearService.clear(scanData);

			assertFalse(Files.exists(embeddedFile), "embedded 파일은 전체 경로로 삭제됨");
		}

		@Test
		@DisplayName("메시지 ID 임시 디렉터리 전체 삭제")
		void messageTempDirectory_shouldBeDeleted() throws Exception {
			Path msgFile = Files.createFile(tempDir.resolve("test.msg"));
			Path memoryRoot = Files.createDirectory(tempDir.resolve("memory"));
			String msgId = "20260702165512.TAZPQY7KGAAAK4ZRZ64GTXZ75IR76EDT";
			Path imageDir = Files.createDirectories(memoryRoot.resolve(msgId).resolve("_img"));
			Files.createFile(imageDir.resolve("embedded.png"));

			MSGData msgData = new MSGData();
			msgData.setMsgid(msgId);
			msgData.setMsgFile(null);
			msgData.setHeader(null);
			msgData.setAppFile(new ArrayList<>());
			msgData.setPcFile(new ArrayList<>());
			msgData.setEmbeddedFile(new ArrayList<>());

			ScanData scanData = mock(ScanData.class);
			when(scanData.getFilePath()).thenReturn(msgFile);
			when(scanData.getMsgData()).thenReturn(msgData);
			when(conf.getMemoryDiskPath()).thenReturn(memoryRoot.toString());

			clearService.clear(scanData);

			assertFalse(Files.exists(memoryRoot.resolve(msgId)), "메시지 ID 임시 디렉터리 삭제됨");
			assertTrue(Files.exists(memoryRoot), "memory root는 유지됨");
		}
	}

	@Nested
	@DisplayName("존재하지 않는 파일 삭제 시도")
	class NonExistentFile {
		@Test
		@DisplayName("존재하지 않는 파일 → 예외 없이 0 반환")
		void nonExistentFile_shouldNotThrow() throws Exception {
			Path msgFile = Files.createFile(tempDir.resolve("test.msg"));

			MSGData msgData = new MSGData();
			msgData.setMsgFile("nonexistent.html");
			msgData.setHeader(null);
			msgData.setAppFile(new ArrayList<>());
			msgData.setPcFile(new ArrayList<>());
			msgData.setEmbeddedFile(new ArrayList<>());
			when(conf.getPath("nonexistent.html", false)).thenReturn(tempDir.resolve("nonexistent.html").toString());

			ScanData scanData = mock(ScanData.class);
			when(scanData.getFilePath()).thenReturn(msgFile);
			when(scanData.getMsgData()).thenReturn(msgData);

			assertDoesNotThrow(() -> clearService.clear(scanData));
		}
	}
}
