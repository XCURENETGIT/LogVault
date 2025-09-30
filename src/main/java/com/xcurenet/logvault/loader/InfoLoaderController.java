package com.xcurenet.logvault.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Log4j2
@RestController
@RequestMapping(path = "/actuator")
@RequiredArgsConstructor
public class InfoLoaderController {

	private final InfoLoader infoLoader;

	@ResponseBody
	@GetMapping(value = "/insa/reload")
	public ResponseEntity<Object> reload() {
		infoLoader.init();
		return ResponseEntity.ok().build();
	}
}
