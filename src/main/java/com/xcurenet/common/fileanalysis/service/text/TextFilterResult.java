package com.xcurenet.common.fileanalysis.service.text;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TextFilterResult {
	private String ext;                          // 예상 확장자
	private int OLECount = 0;                    // OLE 개수
	private boolean hasImages = false;           // 파일 내 이미지 여부
	private List<String> images;                 // 첨부 파일의 이미지 추출 경로 목록
	private String content;                      // 필터링된 전체 텍스트
}
