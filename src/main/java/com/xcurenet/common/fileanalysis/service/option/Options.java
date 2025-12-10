package com.xcurenet.common.fileanalysis.service.option;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "Options", description = "분석을 위한 옵션")
public class Options {
	@Schema(description = "메시지아이디 (없으면 랜덤으로 생성됨)", example = "20251001142642.CTHAXKRMYS7WIVOQEONZUNMEUKFHRSDH")
	private String msgId;

	@Schema(description = "원본 파일명", example = "abc.xlsx")
	private String fileName;

	@Schema(description = "이미지 추출 여부", example = "false")
	private boolean extractImage = false;

	@Schema(description = "압축 내 이미지 탐지 여부", example = "false")
	private boolean checkArchiveImage = false;

	@Schema(description = "압축 내 이미지 탐지 깊이", example = "5")
	private int checkArchiveDepth = 5;


	@Schema(description = "엑셀 숨김 시트 탐지 여부", example = "false")
	private boolean checkExcelHiddenSheet = false;

	@JsonIgnore
	@Schema(hidden = true)
	private String imagePath;

	@Override
	public String toString() {
		return "Options{" +
				", fileName='" + fileName + '\'' +
				", extractImage=" + extractImage +
				", checkArchiveImage=" + checkArchiveImage +
				", checkArchiveDepth=" + checkArchiveDepth +
				", checkExcelHiddenSheet=" + checkExcelHiddenSheet +
				'}';
	}
}
