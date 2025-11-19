package com.xcurenet.logvault.module.analysis;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.thumbnail.FileThumbnail;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
			if (!attach.isExist()) continue;
			attach.setOcrTarget(false);

			StopWatch sw = DateUtils.start();
			JSONObject text = getText(doc.getMsgid(), attach.getSrcPath(), attach.getName());
			if (text != null && text.getBoolean("success")) {
				JSONObject data = text.getJSONObject("data");
				String limit = Common.limitLength(data.getString("text"), conf.getTextLimitLength());
				limit = Common.limitTokenLengthWithSpace(limit, conf.getTextLimitToken());

				attach.setText(limit);
				attach.setExpectedExtension(data.getString("extension"));
				attach.setExpectedUnknown(data.getBoolean("unknownType"));
				attach.setChangeExtension(data.getBoolean("changeExtension"));
				attach.setEncrypted(data.getBoolean("encrypted"));

				// 이미지 추출 정보
				try {
					if (data.get("imagesCount") != null && data.get("imagesBase64") != null && data.getInteger("imagesCount") > 0) {

						EmassDoc.ImageExtractorInfo imageExtractorInfo = new EmassDoc.ImageExtractorInfo();
						imageExtractorInfo.setImageCount(data.getInteger("imagesCount"));

						List<String> hashList = new ArrayList<>();
						JSONArray array = data.getJSONArray("imagesBase64");
						for (int i = 0; i < array.size(); i++) {
							String base64 = array.getString(i);
							if (base64 == null) continue;

							String hash = Common.toHexString(Common.sha256(base64));
							hashList.add(hash);
							fileThumbnail.insertThumbnail(hash, base64);
						}
						imageExtractorInfo.setImageHash(hashList);
						attach.setImageExtractorInfo(imageExtractorInfo);
					}
				} catch (Exception e) {
					log.warn("ATT_OLE_IMG | {} | {}", conf.getDataPathSmall(attach.getSrcPath()), e.getMessage());
				}

				// 엑셀 Hidden Sheet 정보
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

				// OCR 대상 여부
				String ext = Common.nvl(attach.getExtension());
				if (conf.getOcrTargetExt().contains(attach.getExpectedExtension()) || conf.getOcrTargetExt().contains(ext)) {
					attach.setOcrStatus("P"); // PENDING
					attach.setOcrTarget(true);
				}
				log.info("ATT_TEXT | {} | RESULT:{} | TXT_LEN:{} | {}", conf.getDataPathSmall(attach.getSrcPath()), text.get("success"), Common.nvl(attach.getText()).length(), DateUtils.stop(sw));
			} else {
				log.warn("ATT_TEXT | {} | {} | TXT_LEN:{} | {}", conf.getDataPathSmall(attach.getSrcPath()), text, Common.nvl(attach.getText()).length(), DateUtils.stop(sw));
			}
		}
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
