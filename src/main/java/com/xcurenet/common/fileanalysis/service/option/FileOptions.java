package com.xcurenet.common.fileanalysis.service.option;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, doNotUseGetters = true)
@Schema(name = "FileOptions", description = "분석을 위한 파일 바이너리")
public class FileOptions extends Options {
	@Schema(description = "분석 대상 파일")
	private MultipartFile file;
}
