package com.xcurenet.common.fileanalysis.service.option;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, doNotUseGetters = true)
@Schema(name = "PathOptions", description = "분석을 위한 파일 경로")
public class PathOptions extends Options {
	@Schema(description = "파일 경로", example = "/users/abc.xlsx")
	private String filePath;

	@Schema(description = "결과 JSON 반환 여부 (true: JSON 통합 반환, false: 텍스트/이미지 파일 분리 저장)", example = "true")
	private boolean resultAsJson;

	@Schema(description = "텍스트/이미지 분리 저장 시 저장 디렉토리 경로 (resultAsJson=false 일 때만 사용)", example = "/data/analysis/results")
	private String textImgSaveDir;
}

