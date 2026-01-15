package com.xcurenet.logvault.upload;

import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api")
public class FileUploadController {

	private static final Path BASE_DIR = Paths.get("/users/las").toAbsolutePath().normalize();

	@PostMapping(value = "/upload", consumes = "multipart/form-data")
	public ResponseEntity<?> uploadFile(HttpServletRequest request, @RequestParam("file") MultipartFile file, @RequestParam String path) throws Exception {
		StopWatch sw = DateUtils.start();

		Path fullPath = resolveSafeFullPath(request, path);
		Path targetDir = fullPath.getParent();

		String filename = fullPath.getFileName().toString();
		if (!Files.exists(targetDir)) {
			Files.createDirectories(targetDir);
		}

		Path finalFile = targetDir.resolve(filename);
		try (InputStream in = file.getInputStream();
		     OutputStream out = Files.newOutputStream(finalFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			in.transferTo(out);
		}
		Common.setAllPermissions(finalFile.toFile());

		long size = Files.size(finalFile);
		log.info("[UPLOAD_SUCCESS] IP:{} | FILE:{} | SIZE:{} | {}", Common.getClientIp(request), finalFile, size, DateUtils.stop(sw));
		return ResponseEntity.ok(Map.of("status", true, "message", "ok", "size", size));
	}

	private Path resolveSafeFullPath(HttpServletRequest request, String path) {
		if (path == null || path.isEmpty()) {
			throw new IllegalArgumentException("Empty path");
		}

		path = path.replace("\\", "/");
		if (path.contains("..")) {
			log.warn("[UPLOAD_FAIL] IP:{} | PATH:{} | Invalid path", Common.getClientIp(request), path);
			throw new IllegalArgumentException("Invalid path");
		}

		Path resolved;
		if (path.startsWith("/")) {
			resolved = Paths.get(path).normalize();
		} else {
			resolved = BASE_DIR.resolve(path).normalize();
		}

		if (!resolved.startsWith(BASE_DIR)) {
			log.warn("[UPLOAD_FAIL] IP:{} | PATH:{} | BASE_DIR escape detected", Common.getClientIp(request), path);
			throw new IllegalArgumentException("Path traversal detected");
		}
		return resolved;
	}

	@GetMapping("/health")
	public ResponseEntity<?> health() {
		return ResponseEntity.ok(Map.of("status", true, "message", "upload", "time", System.currentTimeMillis()));
	}

	@PostMapping(value = "/upload-test", consumes = "multipart/form-data")
	public ResponseEntity<?> uploadTest(HttpServletRequest request, @RequestParam("file") MultipartFile file, @RequestParam String path) throws Exception {
		StopWatch sw = DateUtils.start();

		Path fullPath = resolveSafeFullPath(request, path);
		Path targetDir = fullPath.getParent();
		String filename = fullPath.getFileName().toString();

		if (!Files.exists(targetDir)) {
			Files.createDirectories(targetDir);
		}

		Path finalFile = targetDir.resolve(filename);
		long size;
		try (InputStream in = file.getInputStream();
		     OutputStream out = Files.newOutputStream(finalFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			in.transferTo(out);
			size = Files.size(finalFile);
		} finally {
			try {
				Files.deleteIfExists(finalFile);
			} catch (IOException e) {
				log.warn("[UPLOAD_TEST_DELETE_FAIL] {}", finalFile, e);
			}
		}
		log.info("[UPLOAD_TEST_OK] IP:{} | FILE:{} | SIZE:{} | {}", Common.getClientIp(request), finalFile, size, DateUtils.stop(sw));
		return ResponseEntity.ok(Map.of("status", true, "message", "upload test ok (file deleted)", "size", size));
	}

}
