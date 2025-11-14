package com.xcurenet.logvault.opensearch;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.ResponseVO;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.job.backup.BackupPolicy;
import com.xcurenet.logvault.job.backup.RestoreManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/opensearch")
@RequiredArgsConstructor
@Tag(name = "OpenSearch RestAPI", description = "OpenSearch 상태정보 조회, 백업, 복구 내용을 처리함.")
public class OpenSearchController {
	private final IndexService indexService;
	private final BackupPolicy backupPolicy;
	private final RestoreManager restoreManager;

	@Operation(summary = "OpenSearch Cluster 상태 정보 조회", description = "OpenSearch Cluster 상태 정보 조회")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@ResponseBody
	@GetMapping(value = "/clusterHealth")
	public ResponseEntity<Object> getClusterHealth() {
		JSONObject result = indexService.getClusterHealth();
		return ResponseEntity.status(200).body(new ResponseVO(true, 200, result, null));
	}

	@Operation(summary = "OpenSearch Index 상태 정보 조회", description = "OpenSearch Index 상태 정보 조회")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@ResponseBody
	@GetMapping(value = "/indexStatus")
	public ResponseEntity<Object> indexStatus() {
		List<Map<String, String>> result = indexService.getIndices();
		return ResponseEntity.status(200).body(new ResponseVO(true, 200, result, null));
	}

	@Operation(summary = "OpenSearch Shard 상태 정보 조회", description = "OpenSearch Shard 상태 정보 조회")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@ResponseBody
	@GetMapping(value = "/shardStatus")
	public ResponseEntity<Object> getShardStatus() {
		JSONArray result = indexService.getShardStatus();
		return ResponseEntity.status(200).body(new ResponseVO(true, 200, result, null));
	}


	@Operation(summary = "OpenSearch 스냅샷 레파지토리 생성", description = "OpenSearch 백업 스냅샷 레파지토리 생성")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@GetMapping("/snapshot/repo")
	public ResponseEntity<Object> createRepo(@RequestParam("location") final String location) {
		JSONObject result = indexService.createRepository(location);
		return ResponseEntity.status(200).body(new ResponseVO(true, 200, result, null));
	}

	@Operation(summary = "OpenSearch 특정 날짜 스냅샷 생성 (yyyymmdd)", description = "OpenSearch 특정 날짜 스냅샷 생성 (yyyymmdd)")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@GetMapping("/snapshot/create")
	public ResponseEntity<Object> createSnapshot(@RequestParam("date") final String date) {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(new ResponseVO(false, HttpStatus.BAD_REQUEST.value(), null, "date is invalid"));
		}

		JSONObject result = indexService.createDailySnapshot(date);
		return ResponseEntity.status(200).body(new ResponseVO(true, 200, result, null));
	}

	@Operation(summary = "OpenSearch 스냅샷 목록 조회", description = "OpenSearch 스냅샷 목록 조회")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@GetMapping("/snapshot/list")
	public ResponseEntity<Object> listSnapshots() {
		JSONObject result = indexService.getSnapshotList();
		return ResponseEntity.status(200).body(new ResponseVO(true, 200, result, null));
	}

	@Operation(summary = "OpenSearch 스냅샷 복구", description = "OpenSearch 스냅샷 복구")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@GetMapping("/snapshot/restoreSnapshot")
	public ResponseEntity<Object> restoreSnapshots(@RequestParam("date") final String date) {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(new ResponseVO(false, HttpStatus.BAD_REQUEST.value(), null, "date is invalid"));
		}

		JSONObject result = indexService.restoreSnapshot(date);
		return ResponseEntity.status(200).body(new ResponseVO(true, 200, result, null));
	}

	@Operation(summary = "데이터 백업", description = "특정 일자의 데이터 백업 (색인, 본문, 첨부)")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@GetMapping("/backup")
	public ResponseEntity<Object> backup(@RequestParam("date") final String date) throws IOException {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(new ResponseVO(false, HttpStatus.BAD_REQUEST.value(), null, "date is invalid"));
		}
		DateTime dateTime = DateUtils.parseDateTimeYYYYMMDD(date);
		boolean result = backupPolicy.backupRun(dateTime);
		return ResponseEntity.status(200).body(new ResponseVO(result, 200, null, null));
	}

	@Operation(summary = "백업 json파일 복구", description = "OpenSearch 백업 json파일 복구")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@GetMapping("/restore")
	public ResponseEntity<Object> restoreJson(@RequestParam("date") final String date) throws IOException {
		if (!DateUtils.validDate(date, DateUtils.YYYYMMDD)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(new ResponseVO(false, HttpStatus.BAD_REQUEST.value(), null, "date is invalid"));
		}
		DateTime dateTime = DateUtils.parseDateTimeYYYYMMDD(date);
		boolean result = restoreManager.restore(dateTime);
		return ResponseEntity.status(200).body(new ResponseVO(result, 200, null, null));
	}

	@Operation(summary = "백업 목록", description = "백업된 파일 정보")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "성공적으로 목록 반환"), @ApiResponse(responseCode = "500", description = "서버 내부 오류")})
	@GetMapping("/backupList")
	public ResponseEntity<Object> getBackupList() throws IOException {
		JSONArray result = backupPolicy.getBackupList();
		return ResponseEntity.status(200).body(new ResponseVO(true, 200, result, null));
	}
}
