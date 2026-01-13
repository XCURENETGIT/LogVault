package com.xcurenet.logvault.upload;

import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
	public ResponseEntity<?> uploadFile(HttpServletRequest request, @RequestParam("file") MultipartFile file, @RequestParam String filename, @RequestParam String path) throws Exception {
		StopWatch sw = DateUtils.start();

		Path targetDir = resolveSafePath(request, filename, path);
		Files.createDirectories(targetDir);

		Path tempFile = targetDir.resolve(filename + ".uploading");
		Path finalFile = targetDir.resolve(filename);
		try (InputStream in = file.getInputStream();
		     OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			in.transferTo(out);
		}
		Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		Common.setAllPermissions(finalFile.toFile());

		long size = Files.size(finalFile);
		log.info("[UPLOAD_SUCCESS] IP:{} | FILE:{} | PATH:{} | SIZE:{} | {}", Common.getClientIp(request), filename, path, size, DateUtils.stop(sw));
		return ResponseEntity.ok(Map.of("status", true, "message", "ok", "size", size));
	}


	/**
	 * Resolves a user path safely, preventing traversal attacks
	 */
	private Path resolveSafePath(HttpServletRequest request, String filename, String path) {
		if (path.contains("..") || path.startsWith("\\")) {
			log.warn("[UPLOAD_FAIL] IP:{} | FILE:{} | PATH:{} | Invalid path", Common.getClientIp(request), filename, path);
			throw new IllegalArgumentException("Invalid path");
		}

		Path resolved = BASE_DIR.resolve(path).normalize();
		if (!resolved.startsWith(BASE_DIR)) {
			log.warn("[UPLOAD_FAIL] IP:{} | FILE:{} | PATH:{} | Path traversal detected", Common.getClientIp(request), filename, path);
			throw new IllegalArgumentException("Path traversal detected");
		}
		return resolved;
	}
}
