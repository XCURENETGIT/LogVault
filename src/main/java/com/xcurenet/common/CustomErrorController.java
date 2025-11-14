package com.xcurenet.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
public class CustomErrorController implements ErrorController {

	@RequestMapping("/error")
	public ResponseEntity<ResponseVO> handleError(HttpServletRequest request) {
		Object status = request.getAttribute("jakarta.servlet.error.status_code");
		HttpStatus httpStatus = HttpStatus.valueOf(status != null ? (int) status : 500);
		log.warn("Error occurred: status={}, uri={}, message={}", httpStatus.value(), request.getRequestURI(), httpStatus.getReasonPhrase());
		return ResponseEntity.status(httpStatus).body(new ResponseVO(false, httpStatus.value(), null, httpStatus.getReasonPhrase()));
	}
}
