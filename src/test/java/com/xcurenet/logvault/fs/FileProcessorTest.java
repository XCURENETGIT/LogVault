package com.xcurenet.logvault.fs;

import com.xcurenet.logvault.conf.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileProcessor - 파일시스템 위임 로직")
class FileProcessorTest {

	@Mock
	private Config conf;
	@Mock
	private FileSystemService localFileSystem;
	@Mock
	private FileSystemService minioFileSystem;

	private FileProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new FileProcessor(conf, localFileSystem, minioFileSystem);
	}

	@Nested
	@DisplayName("init() - 파일시스템 타입에 따른 서비스 선택")
	class Init {
		@Test
		@DisplayName("local → LocalFileSystem 선택")
		void local_shouldSelectLocal() throws Exception {
			when(conf.getFileSystemType()).thenReturn("local");
			processor.init();
			assertEquals(localFileSystem, processor.getService());
		}

		@Test
		@DisplayName("minio → MinioFileSystem 선택")
		void minio_shouldSelectMinio() throws Exception {
			when(conf.getFileSystemType()).thenReturn("minio");
			processor.init();
			assertEquals(minioFileSystem, processor.getService());
		}

		@Test
		@DisplayName("init() 호출 시 선택된 서비스의 init()도 호출")
		void init_shouldCallServiceInit() throws Exception {
			when(conf.getFileSystemType()).thenReturn("local");
			processor.init();
			verify(localFileSystem).init();
		}
	}

	@Nested
	@DisplayName("위임 메서드")
	class Delegation {

		@BeforeEach
		void initLocal() throws Exception {
			when(conf.getFileSystemType()).thenReturn("local");
			processor.init();
		}

		@Test
		@DisplayName("exists() → 서비스에 위임")
		void exists() {
			when(localFileSystem.exists("/path")).thenReturn(true);
			assertTrue(processor.exists("/path"));
			verify(localFileSystem).exists("/path");
		}

		@Test
		@DisplayName("delete() → 서비스에 위임")
		void delete() {
			when(localFileSystem.delete("/path")).thenReturn(true);
			assertTrue(processor.delete("/path"));
		}

		@Test
		@DisplayName("write(src, dst, fileName) → 서비스에 위임")
		void write() throws Exception {
			processor.write("/src", "/dst", "file.zip");
			verify(localFileSystem).write("/src", "/dst", "file.zip");
		}

		@Test
		@DisplayName("getTotalSpace() - 빈 경로 → 0 반환")
		void totalSpace_emptyPath() {
			assertEquals(0, processor.getTotalSpace(""));
		}

		@Test
		@DisplayName("getUsableSpace() - null 경로 → 0 반환")
		void usableSpace_nullPath() {
			assertEquals(0, processor.getUsableSpace(null));
		}

		@Test
		@DisplayName("size() - 빈 경로 → 0 반환")
		void size_emptyPath() {
			assertEquals(0, processor.size(""));
		}
	}
}
