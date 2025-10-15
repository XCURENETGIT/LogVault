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

	@ResponseBody
	@GetMapping(value = "/insa/user/reload")
	public ResponseEntity<Object> userReload() {
		infoLoader.userLoad();
		return ResponseEntity.ok().build();
	}

	@ResponseBody
	@GetMapping(value = "/insa/keyword/reload")
	public ResponseEntity<Object> keywordReload() {
		infoLoader.keywordLoad();
		return ResponseEntity.ok().build();
	}

	@ResponseBody
	@GetMapping(value = "/insa/pattern/reload")
	public ResponseEntity<Object> patternReload() {
		infoLoader.patternLoad();
		return ResponseEntity.ok().build();
	}
}
