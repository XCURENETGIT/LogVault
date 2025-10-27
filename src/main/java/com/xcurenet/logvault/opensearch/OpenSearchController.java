package com.xcurenet.logvault.opensearch;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/opensearch")
@RequiredArgsConstructor
public class OpenSearchController {
	private final IndexService indexService;

	@ResponseBody
	@GetMapping(value = "/indexstoresizes")
	public ResponseEntity<Object> reload() {
		return ResponseEntity.ok(indexService.getIndices());
	}
}
