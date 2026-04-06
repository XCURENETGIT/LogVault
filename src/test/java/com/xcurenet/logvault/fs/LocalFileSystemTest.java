package com.xcurenet.logvault.fs;

import com.xcurenet.logvault.conf.Config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalFileSystem - 로컬 파일 I/O")
class LocalFileSystemTest {

	@Mock
	private Config conf;
	private LocalFileSystem fs;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		lenient().when(conf.isEncryptEnable()).thenReturn(false); // 테스트 시 암호화 비활성화
		fs = new LocalFileSystem(conf);
	}

	@Nested
	@DisplayName("exists()")
	class Exists {
		@Test
		@DisplayName("존재하는 파일 → true")
		void existing() throws Exception {
			Path f = Files.createFile(tempDir.resolve("test.txt"));
			assertTrue(fs.exists(f.toString()));
		}

		@Test
		@DisplayName("존재하지 않는 파일 → false")
		void nonExisting() {
			assertFalse(fs.exists(tempDir.resolve("nope.txt").toString()));
		}
	}

	@Nested
	@DisplayName("write() & open()")
	class WriteAndOpen {
		@Test
		@DisplayName("파일 쓰기 후 읽기 (암호화 비활성)")
		void writeAndRead() throws Exception {
			Path src = Files.writeString(tempDir.resolve("src.txt"), "hello world");
			Path dst = tempDir.resolve("sub/dst.txt");

			fs.write(src.toString(), dst.toString(), "dst.txt");

			assertTrue(Files.exists(dst));
			assertEquals("hello world", Files.readString(dst));
		}

		@Test
		@DisplayName("부모 디렉터리 자동 생성")
		void parentDirCreated() throws Exception {
			Path src = Files.writeString(tempDir.resolve("src.txt"), "data");
			Path dst = tempDir.resolve("a/b/c/dst.txt");

			fs.write(src.toString(), dst.toString(), "dst.txt");
			assertTrue(Files.exists(dst.getParent()));
		}
	}

	@Nested
	@DisplayName("writeText()")
	class WriteText {
		@Test
		@DisplayName("텍스트 직접 쓰기")
		void writeText() throws Exception {
			Path p = tempDir.resolve("text.txt");
			fs.writeText(p.toString(), "테스트 데이터");
			assertEquals("테스트 데이터", Files.readString(p));
		}
	}

	@Nested
	@DisplayName("delete()")
	class Delete {
		@Test
		@DisplayName("존재하는 파일 삭제 → true")
		void deleteExisting() throws Exception {
			Path f = Files.createFile(tempDir.resolve("del.txt"));
			assertTrue(fs.delete(f.toString()));
			assertFalse(Files.exists(f));
		}

		@Test
		@DisplayName("존재하지 않는 파일 삭제 → false")
		void deleteNonExisting() {
			assertFalse(fs.delete(tempDir.resolve("nope.txt").toString()));
		}
	}

	@Nested
	@DisplayName("deleteDirectory()")
	class DeleteDirectory {
		@Test
		@DisplayName("디렉터리와 하위 파일 전부 삭제")
		void deleteDir() throws Exception {
			Path sub = tempDir.resolve("dir/sub");
			Files.createDirectories(sub);
			Files.writeString(sub.resolve("file.txt"), "data");

			assertTrue(fs.deleteDirectory(tempDir.resolve("dir").toString()));
			assertFalse(Files.exists(tempDir.resolve("dir")));
		}

		@Test
		@DisplayName("존재하지 않는 디렉터리 → true (이미 없으므로)")
		void deleteNonExistingDir() {
			assertTrue(fs.deleteDirectory(tempDir.resolve("nonexist").toString()));
		}
	}

	@Nested
	@DisplayName("status()")
	class Status {
		@Test
		@DisplayName("존재하는 파일 → XcnFileStatus 반환")
		void existing() throws Exception {
			Path f = Files.writeString(tempDir.resolve("status.txt"), "abc");
			XcnFileStatus st = fs.status(f.toString());
			assertNotNull(st);
			assertEquals(3, st.getLen());
			assertFalse(st.isDirectory());
		}

		@Test
		@DisplayName("존재하지 않는 파일 → null")
		void nonExisting() {
			assertNull(fs.status(tempDir.resolve("nope").toString()));
		}
	}

	@Nested
	@DisplayName("size()")
	class Size {
		@Test
		@DisplayName("파일 크기 반환")
		void fileSize() throws Exception {
			Path f = Files.writeString(tempDir.resolve("size.txt"), "12345");
			assertEquals(5, fs.size(f.toString()));
		}

		@Test
		@DisplayName("존재하지 않는 파일 → 0")
		void nonExisting() {
			assertEquals(0, fs.size(tempDir.resolve("nope").toString()));
		}
	}
}
