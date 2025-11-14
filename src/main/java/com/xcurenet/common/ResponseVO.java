package com.xcurenet.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "결과 반환 객체", requiredProperties = {"success", "code"})
public class ResponseVO {
	/**
	 * 결과 성공 여부 (true/false)
	 */
	@NotNull
	@Schema(description = "결과 성공 여부", example = "true or false")
	private boolean success;

	/**
	 * 결과 코드 값 (200, 500 등)
	 */
	@NotNull
	@Schema(description = "결과 코드 값", example = "200, 500")
	private int code;

	/**
	 * 결과 데이터
	 */
	@Schema(description = "결과 데이터")
	private Object data;

	/**
	 * 실패에 대한 오류 메시지
	 */
	@Schema(description = "실패에 대한 오류 메시지", example = "NullPointException")
	private String message;
}
