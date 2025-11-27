package com.xcurenet.logvault.module.analysis;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.thumbnail.FileThumbnail;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Log4j2
@Service
@RequiredArgsConstructor
public class AttachAnalysis {
	private final RestClient restClient;
	private final Config conf;
	private final FileThumbnail fileThumbnail;

	private JSONObject getText(final String msgId, final String filePath, final String fileName) {
		LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("msgId", msgId);
		body.add("filePath", filePath);
		body.add("fileName", fileName);
		body.add("extractImage", conf.isExtractImage());
		body.add("checkArchiveImage", conf.isExtractImage());
		body.add("checkArchiveDepth", conf.getDecompressDepth());
		body.add("checkExcelHiddenSheet", conf.isCheckExcelHiddenSheet());

		int maxRetries = 3;
		int attempt = 0;
		while (attempt < maxRetries) {
			try {
				attempt++;
				return restClient.post().uri(conf.getFileAnalysisUrl()).contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(JSONObject.class);
			} catch (Exception e) {
				log.warn("GET_TEXT | {} | ({}/{}) | {}", filePath, attempt, maxRetries, e.getMessage());
				if (attempt < maxRetries) Common.sleep(1000);
			}
		}
		return null;
	}

	public void setAttachText(final ScanData msg) {
		EmassDoc doc = msg.getEmassDoc();
		List<EmassDoc.Attach> attaches = doc.getAttach();
		if (attaches == null) return;

		for (EmassDoc.Attach attach : attaches) {
			attach.setOcrTarget(false);
			attach.setExpectedUnknown(true);
			attach.setChangeExtension(false);
			attach.setEncrypted(false);

			// 설정된 사이즈보다 큰 파일의 경우 텍스트 추출을 하지 않는다.
			if (!attach.isExist() || isFileOverSize(attach.getSrcPath(), conf.getFileAnalysisLimitSize())) {
				continue;
			}

			StopWatch sw = DateUtils.start();
			JSONObject text = getText(doc.getMsgid(), attach.getSrcPath(), attach.getName());
			if (text != null && text.getBoolean("success")) {
				JSONObject data = text.getJSONObject("data");
				String limitText = Common.limitLength(data.getString("text"), conf.getTextLimitLength());
				limitText = Common.limitTokenLengthWithSpace(limitText, conf.getTextLimitToken()); // 텍스트 추출 후 최대 사이즈 제한까지 등록

				attach.setText(limitText);
				attach.setExpectedExtension(data.getString("extension"));       // 예상 확장자
				attach.setExpectedUnknown(data.getBoolean("unknownType"));      // 알수없는 확장자
				attach.setChangeExtension(data.getBoolean("changeExtension"));  // 확장자 변경 유무
				attach.setEncrypted(data.getBoolean("encrypted"));              // 암호화 유무
				setEmbeddedImage(msg.getMsgData(), attach, data);                                     // 파일내 이미지 추출 정보
				setExcelHiddenSheet(attach, data);                                  // 엑셀 히드시트 정보 추가
				setOCRTarget(attach);                                               // OCR 대상 설정

				log.info("ATT_TEXT | {} | RESULT:{} | TXT_LEN:{} | {}", conf.getDataPathSmall(attach.getSrcPath()), text.get("success"), Common.nvl(attach.getText()).length(), DateUtils.stop(sw));
			} else {
				log.warn("ATT_TEXT | {} | {} | TXT_LEN:{} | {}", conf.getDataPathSmall(attach.getSrcPath()), text, Common.nvl(attach.getText()).length(), DateUtils.stop(sw));
			}
		}
	}

	// 파일내 이미지 추출 정보
	private void setEmbeddedImage(MSGData msgData, EmassDoc.Attach attach, JSONObject data) {
		try {
			if (data.get("imagesCount") != null && data.get("embeddedImage") != null && data.getInteger("imagesCount") > 0) {
				JSONArray array = data.getJSONArray("embeddedImage");
				List<String> embeddedFiles = msgData.getEmbeddedFile();
				List<EmassDoc.ImageExtractorInfo> imageExtractorInfos = new ArrayList<>();
				for (int i = 0; i < array.size(); i++) {
					JSONObject embedded = array.getJSONObject(i);
					String base64 = embedded.getString("base64");
					if (base64 == null) continue;

					String ext = FilenameUtils.getExtension(embedded.getString("name"));
					String name = Common.makeMD5Hex(Paths.get(attach.getSrcPath()).getFileName().toString(), embedded.getString("name")) + "." + ext; //첨부이름 + 이미지 이름 MD5
					String srcPath = Common.makeFilepath(Paths.get(attach.getSrcPath()).getParent().toString(), name); //임시 SRC 저장 위치
					String dest = conf.getDestPath(msgData.getCtime(), msgData.getMsgid(), name); //최종 저장 위치
					Files.write(Path.of(Objects.requireNonNull(srcPath)), Common.decodeBase64(base64), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

					embeddedFiles.add(srcPath);

					log.info("OLE__IMG | {} | {}", embedded.getString("name"), Common.getBase64Size(base64));
					imageExtractorInfos.add(EmassDoc.ImageExtractorInfo.builder().name(embedded.getString("name")).path(dest).build());
				}
				msgData.setEmbeddedFile(embeddedFiles); //파일 전송을 위한 저장
				attach.setImageExtractorInfo(imageExtractorInfos);

				if (conf.isOcrEmbeddedImageEnable()) {
					attach.setOcrStatus("P"); // PENDING, 파일 내부에 있는 이미지도 OCR 대상임
					attach.setOcrTarget(true);
				}
			}
		} catch (Exception e) {
			log.warn("ATT_OLE_IMG | {} | {}", conf.getDataPathSmall(attach.getSrcPath()), e.getMessage());
		}
	}

	// OCR 대상 여부
	private void setOCRTarget(EmassDoc.Attach attach) {
		String ext = Common.nvl(attach.getExtension());
		// OCR 사이즈 제한보다 작아야 되며, 예상확장자 혹은 파일의 확장자가 이미지 타입인 경우 OCR 대상임.
		if (!isFileOverSize(attach.getSrcPath(), conf.getOcrLimitSize()) && (conf.getOcrTargetExt().contains(attach.getExpectedExtension()) || conf.getOcrTargetExt().contains(ext))) {
			attach.setOcrStatus("P"); // PENDING
			attach.setOcrTarget(true);
		}
	}

	// 엑셀 Hidden Sheet 정보
	private void setExcelHiddenSheet(EmassDoc.Attach attach, JSONObject data) {
		try {
			if (data.get("sheetInfo") != null) {
				JSONObject sheet = data.getJSONObject("sheetInfo");
				EmassDoc.SheetInfo sheetInfo = new EmassDoc.SheetInfo();
				sheetInfo.setSheetTotal(sheet.getInteger("sheetTotal"));
				sheetInfo.setSheetHiddenTotal(sheet.getInteger("sheetHiddenTotal"));
				sheetInfo.setHiddenSheetNames(sheet.getJSONArray("hiddenSheetNames").toJavaList(String.class));
				attach.setSheetInfo(sheetInfo);
			}
		} catch (Exception e) {
			log.warn("ATT_SHEET | {} | {}", conf.getDataPathSmall(attach.getSrcPath()), e.getMessage());
		}
	}

	private boolean isFileOverSize(final String filePath, final long limitSize) {
		try {
			Path path = Paths.get(filePath);
			if (Files.size(path) > limitSize) return true;
		} catch (IOException e) {
			log.warn("{} | {} | path:{} err={}", ErrorCode.FILE_ANALYSIS_SIZE, ErrorCode.fromCode(ErrorCode.FILE_ANALYSIS_SIZE), filePath, e.toString());
			return true;
		}
		return false;
	}

	public void setAttachThumbnail(final ScanData msg) {
		EmassDoc doc = msg.getEmassDoc();
		try {
			List<EmassDoc.Attach> attaches = doc.getAttach();
			if (attaches == null) return;

			for (EmassDoc.Attach attach : attaches) {
				if (!attach.isExist()) continue;

				Path path = Paths.get(attach.getSrcPath());
				if (!Files.exists(path)) continue;

				if (!fileThumbnail.isExistThumbnail(attach.getHash())) {
					StopWatch sw = DateUtils.start();
					String thumbnail = fileThumbnail.execute(attach.getExpectedExtension(), path, attach.getText());
					if (thumbnail != null) {
						fileThumbnail.insertThumbnail(attach.getHash(), thumbnail);
						log.info("THUMNAIL | {} | {}", conf.getDataPathSmall(attach.getSrcPath()), DateUtils.stop(sw));
					}
				}
			}
		} catch (Exception e) {
			log.warn("THUMNAIL | {}", e.getMessage());
		}
	}

	public static void main(String[] args) {
		StopWatch sw = new StopWatch();
		sw.start();
		for (int i = 1; i <= 100; i++) {
			Common.sleep(1);
		}
		sw.stop();
		String formatted = DurationFormatUtils.formatDuration(sw.getTotalTimeMillis(), "s.SSS's'");
		System.out.println(formatted);
	}
}
