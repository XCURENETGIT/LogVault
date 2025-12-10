package com.xcurenet.common.utils;

import lombok.extern.log4j.Log4j2;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Log4j2
public class CompressUtil {

	public static final String CP949 = "CP949";
	private static final String UNARCHIVE_FILE_PATH = "/users/tmp/unarchive/";

	public record UnArchiveFile(File file, String path, String name) {
	}

	public static void main(String[] args) {
		// depth=2 → 루트/하위폴더/그 아래까지만 추출
		List<UnArchiveFile> files = CompressUtil.unArchiveFile("/users/tmp/relatedSite.zip", "zip", 4);
		log.info("Extracted {} files", files.size());
	}

	/**
	 * 메인 진입점: 확장자에 따라 적절한 Extractor 선택
	 */
	public static List<UnArchiveFile> unArchiveFile(String filePath, String ext, int depth) {
		return switch (ext.toLowerCase()) {
			case "zip", "zipx" -> extractZip(filePath, depth);
			case "tar" -> extractTar(filePath, depth);
			case "7z" -> extract7z(filePath, depth);
			case "gz" -> extractGz(filePath, depth);
			case "rar" -> extractRar(filePath, depth);
			default -> Collections.emptyList();
		};
	}

	private static List<UnArchiveFile> extractZip(String filePath, int depth) {
		List<UnArchiveFile> results = new ArrayList<>();
		try (ZipInputStream in = new ZipInputStream(new FileInputStream(filePath), Charset.forName(CP949))) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				if (exceedsDepth(entry.getName(), depth)) continue;
				if (entry.isDirectory()) {
					results.add(writeEntry(filePath, entry.getName(), true, InputStream.nullInputStream()));
				} else {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					IOUtils.copy(in, baos);
					results.add(writeEntry(filePath, entry.getName(), false, new ByteArrayInputStream(baos.toByteArray())));
				}
			}
		} catch (Exception e) {
			log.error("Error extracting zip", e);
		}
		return results;
	}

	private static List<UnArchiveFile> extractTar(String filePath, int depth) {
		List<UnArchiveFile> results = new ArrayList<>();
		try (TarArchiveInputStream in = new TarArchiveInputStream(new FileInputStream(filePath))) {
			TarArchiveEntry entry;
			while ((entry = in.getNextTarEntry()) != null) {
				if (exceedsDepth(entry.getName(), depth)) continue;
				if (entry.isDirectory()) {
					results.add(writeEntry(filePath, entry.getName(), true, InputStream.nullInputStream()));
				} else {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					IOUtils.copy(in, baos);
					results.add(writeEntry(filePath, entry.getName(), false, new ByteArrayInputStream(baos.toByteArray())));
				}
			}
		} catch (Exception e) {
			log.error("Error extracting tar", e);
		}
		return results;
	}

	private static List<UnArchiveFile> extract7z(String filePath, int depth) {
		List<UnArchiveFile> results = new ArrayList<>();
		File source = new File(filePath);
		try (SevenZFile sevenZFile = new SevenZFile(source)) {
			SevenZArchiveEntry entry;
			while ((entry = sevenZFile.getNextEntry()) != null) {
				if (exceedsDepth(entry.getName(), depth)) continue;
				if (entry.isDirectory()) {
					results.add(writeEntry(filePath, entry.getName(), true, InputStream.nullInputStream()));
				} else {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte[] buffer = new byte[8192];
					int size;
					while ((size = sevenZFile.read(buffer)) > 0) {
						baos.write(buffer, 0, size);
					}
					results.add(writeEntry(filePath, entry.getName(), false, new ByteArrayInputStream(baos.toByteArray())));
				}
			}
		} catch (Exception e) {
			log.error("Error extracting 7z", e);
		} finally {
			try {
				Files.deleteIfExists(source.toPath());
			} catch (IOException ignored) {
			}
		}
		return results;
	}

	private static List<UnArchiveFile> extractGz(String filePath, int depth) {
		List<UnArchiveFile> results = new ArrayList<>();
		String fileName = new File(filePath).getName().replaceAll("\\.gz$", "");
		if (exceedsDepth(fileName, depth)) return results;
		try (GZIPInputStream in = new GZIPInputStream(new FileInputStream(filePath))) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			IOUtils.copy(in, baos);
			results.add(writeEntry(filePath, fileName, false, new ByteArrayInputStream(baos.toByteArray())));
		} catch (Exception e) {
			log.error("Error extracting gz", e);
		}
		return results;
	}

	private static List<UnArchiveFile> extractRar(String filePath, int depth) {
		List<UnArchiveFile> results = new ArrayList<>();
		try (RandomAccessFile raf = new RandomAccessFile(filePath, "r"); IInArchive inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(raf))) {
			int itemCount = inArchive.getNumberOfItems();
			for (int i = 0; i < itemCount; i++) {
				boolean isDir = Boolean.TRUE.equals(inArchive.getProperty(i, PropID.IS_FOLDER));
				String entryName = Objects.toString(inArchive.getProperty(i, PropID.PATH), "");

				if (exceedsDepth(entryName, depth)) continue;

				if (isDir) {
					results.add(writeEntry(filePath, entryName, true, InputStream.nullInputStream()));
				} else {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					inArchive.extractSlow(i, (data) -> {
						try {
							baos.write(data);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						return data.length;
					});
					results.add(writeEntry(filePath, entryName, false, new ByteArrayInputStream(baos.toByteArray())));
				}
			}
		} catch (Exception e) {
			log.error("Error extracting RAR", e);
		}
		return results;
	}

	/**
	 * 공통 파일 쓰기 메서드
	 */
	private static UnArchiveFile writeEntry(String baseFilePath, String entryName, boolean isDir, InputStream in) throws IOException {
		String prefix = Common.toHexString(Common.md5(baseFilePath), false);
		File outDir = new File(UNARCHIVE_FILE_PATH, prefix);
		File file = new File(outDir, entryName);

		FileUtils.forceMkdir(file.isDirectory() ? file : file.getParentFile());

		if (!isDir) {
			try (in; FileOutputStream fos = new FileOutputStream(file)) {
				IOUtils.copy(in, fos);
			}
		}
		log.info("Extracted > {}", file.getAbsolutePath());
		return new UnArchiveFile(file, file.getAbsolutePath(), entryName);
	}

	/**
	 * depth 초과 여부 검사
	 */
	private static boolean exceedsDepth(String entryName, int depth) {
		if (depth <= 0) return false; // 0이면 무제한
		String normalized = entryName.replaceAll("\\\\", "/");
		int actualDepth = normalized.endsWith("/") ? normalized.split("/").length - 1 : normalized.split("/").length;
		return actualDepth > depth;
	}
}
