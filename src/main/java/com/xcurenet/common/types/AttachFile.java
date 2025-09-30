package com.xcurenet.common.types;

import lombok.Data;

@Data
public class AttachFile {
	private String attachId;        // 실제 파일명
	private String pcFileName;      // 실제 파일명
	private String fileName;        // 처리 후 파일명
	private String filePath;        // 파일 전체 경로
	private String destPath;        // 최종 저장될 전체 경로
	private boolean isAttachExist;  // 실제 존재 여부
	private boolean bodyImage;      // 본문 이미지 여부
	private long size;              // 파일 사이즈
	private AttachExtension extension;

	private String fLink;
	private String fLinkKey;
}
