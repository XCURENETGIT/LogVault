package com.xcurenet.common.file;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public abstract class CustomDirectoryWalker extends SimpleFileVisitor<Path> {
	/**
	 * 디렉토리 내 파일을 스캔하고 `handleFile` 메서드를 호출
	 *
	 * @param rootPath 스캔 시작 디렉토리
	 * @throws IOException 파일 순회 중 오류 발생 시
	 */
	public void walk(Path rootPath) throws IOException {
		if (!Files.isDirectory(rootPath)) {
			throw new IllegalArgumentException("유효하지 않은 디렉토리 경로입니다: " + rootPath);
		}
		Files.walkFileTree(rootPath, this);
	}

	/**
	 * 파일 방문 시 호출되는 메서드 (하위 클래스에서 구현)
	 *
	 * @param file  방문한 파일
	 * @param attrs 파일 속성
	 */
	protected abstract void handleFile(Path file, BasicFileAttributes attrs) throws IOException;

	@NotNull
	@Override
	public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
		handleFile(file, attrs);
		return FileVisitResult.CONTINUE;
	}

	@NotNull
	@Override
	public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
		return FileVisitResult.CONTINUE;
	}
}
