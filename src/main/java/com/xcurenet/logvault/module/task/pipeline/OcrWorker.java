package com.xcurenet.logvault.module.task.pipeline;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.FileUtil;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.device.DeviceEndpointResolver;
import com.xcurenet.logvault.device.DeviceServiceKey;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.module.analysis.AnomalyScoreCalculator;
import com.xcurenet.logvault.module.analysis.KeywordAnalysis;
import com.xcurenet.logvault.module.analysis.PrivacyAIAnalysis;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Log4j2
@Component
public class OcrWorker implements PipelineWorker {

	private static final String URL_FMT = "http://%s:%s";
	private final Config conf;
	private final FileProcessor fileProcessor;
	private final IndexService indexService;
	private final KeywordAnalysis keywordAnalysis;
	private final PrivacyAIAnalysis privacyAnalysis;
	private final AnomalyScoreCalculator anomalyScoreCalculator;
	private final RestTemplate restTemplate;
	private final DeviceEndpointResolver endpointResolver;

	public OcrWorker(Config conf, FileProcessor fileProcessor, IndexService indexService, KeywordAnalysis keywordAnalysis, PrivacyAIAnalysis privacyAnalysis, AnomalyScoreCalculator anomalyScoreCalculator, @Qualifier("ocrRestTemplate") RestTemplate restTemplate, DeviceEndpointResolver endpointResolver) {
		this.conf = conf;
		this.fileProcessor = fileProcessor;
		this.indexService = indexService;
		this.keywordAnalysis = keywordAnalysis;
		this.privacyAnalysis = privacyAnalysis;
		this.anomalyScoreCalculator = anomalyScoreCalculator;
		this.restTemplate = restTemplate;
		this.endpointResolver = endpointResolver;
	}

	@Override
	public String getTaskType() {
		return "OCR";
	}

	@Override
	public boolean isEnabled() {
		return conf.isOcrApiEnable();
	}

	@Override
	public int getWorkerCount() {
		return conf.getTaskQueueOcrThreads();
	}

	@Override
	public boolean isTarget(EmassDoc doc) {
		List<EmassDoc.Attach> att = doc.getAttach();
		boolean flag = att != null && att.stream().anyMatch(EmassDoc.Attach::isOcrTarget);
		log.info("OCR__NOT | ATTACH_COUNT:{} | TARGET_COUNT:{}", (att == null ? 0 : att.size()), flag ? att.stream().filter(EmassDoc.Attach::isOcrTarget).count() : 0);
		return flag;
	}

	@Override
	public EmassDoc process(EmassDoc doc) throws Exception {
		StopWatch sw = DateUtils.start();
		int success = 0, fail = 0, target = 0;
		for (EmassDoc.Attach a : doc.getAttach()) {
			if (!a.isExist() || !a.isOcrTarget()) continue;
			if (isOcrFile(a)) {
				target++;
				if (ocrFile(a)) success++;
				else fail++;
			} else if (conf.isOcrEmbeddedImageEnable()) {
				int[] r = ocrEmbedded(a);
				target += r[0];
				success += r[1];
				fail += r[2];
			}
		}
		if (success > 0) {
			doc.setKeywordInfo(null);
			doc.setPrivacyInfo(null);
			doc.setPrivacyTotal(0);
			keywordAnalysis.detect(doc);
			privacyAnalysis.detect(doc);
		}
		EmassDoc.ProcessStatus st = doc.getProcessStatus() == null ? EmassDoc.ProcessStatus.builder().build() : doc.getProcessStatus();
		st.setOcr("E");
		doc.setProcessStatus(st);
		anomalyScoreCalculator.calculate(doc);
		indexService.index(doc);
		log.info("OCR__END | T:{} S:{} F:{} | {}", target, success, fail, DateUtils.stop(sw));
		return doc;
	}

	private boolean isOcrFile(EmassDoc.Attach a) {
		String ext = FileUtil.getExtension(a.getName());
		return conf.getOcrTargetExt().contains(a.getExpectedExtension()) || conf.getOcrTargetExt().contains(ext);
	}

	private boolean ocrFile(EmassDoc.Attach a) {
		StopWatch sw = DateUtils.start();
		try (InputStream in = fileProcessor.open(a.getPath())) {
			String text = callOcr(in, a.getName());
			a.setText(Common.nvl(a.getText()) + "\n" + text);
			a.setOcrStatus("S");
			a.setOcrRate(sw.getTotalTimeMillis());
			log.info("OCR_TEXT | {} | len:{} | {}", conf.getDestPathSmall(a.getPath()), Common.nvl(text).length(), DateUtils.stop(sw));
			return true;
		} catch (Exception e) {
			log.warn("OCR_WARN | {} | {}", conf.getDestPathSmall(a.getPath()), e.getMessage(), e);
			a.setOcrStatus("E");
			a.setOcrRate(sw.getTotalTimeMillis());
			return false;
		}
	}

	private int[] ocrEmbedded(EmassDoc.Attach a) {
		int t = 0, s = 0, f = 0;
		List<EmassDoc.ImageExtractorInfo> imgs = a.getImageExtractorInfo();
		if (imgs == null) return new int[]{0, 0, 0};
		for (EmassDoc.ImageExtractorInfo img : imgs) {
			if (img.getPath() == null) continue;
			try {
				if (Files.size(Paths.get(img.getPath())) > conf.getOcrLimitSize()) continue;
			} catch (IOException e) {
				continue;
			}
			t++;
			StopWatch sw = DateUtils.start();
			try (InputStream in = fileProcessor.open(img.getPath())) {
				String text = callOcr(in, img.getName());
				a.setText(Common.nvl(a.getText()) + "\n" + text);
				a.setOcrStatus("S");
				a.setOcrRate(sw.getTotalTimeMillis());
				s++;
			} catch (Exception e) {
				log.warn("OCR_EMBED | {}", e.getMessage());
				a.setOcrStatus("E");
				f++;
			}
		}
		return new int[]{t, s, f};
	}

	private String callOcr(InputStream in, String name) throws Exception {
		if (Common.isOrEquals(conf.getOcrApiType(), "LC", "LG")) return xcn_ocr_version(in, name);
		if (Common.isEquals(conf.getOcrApiType(), "LGX")) return tmp_gpu_ocr_version(IOUtils.toByteArray(in));
		if (Common.isEquals(conf.getOcrApiType(), "SY")) return synap_ocr_version(in, name);
		return null;
	}

	private String xcn_ocr_version(InputStream in, String name) throws IOException {
		byte[] bytes = in.readAllBytes();
		return xcn_ocr_version(appendPath(ocrBaseUrl(), "/ocr"), bytes, name);
	}

	private String xcn_ocr_version(String url, byte[] bytes, String name) throws IOException {
		ByteArrayResource res = new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return name;
			}
		};
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", res);
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.MULTIPART_FORM_DATA);
		h.setAccept(List.of(MediaType.APPLICATION_JSON));
		ResponseEntity<String> r = restTemplate.postForEntity(url, new HttpEntity<>(body, h), String.class);
		if (!r.getStatusCode().is2xxSuccessful()) throw new IOException("HTTP " + r.getStatusCode());
		JSONObject j = JSONObject.parseObject(r.getBody());
		return j != null ? j.getString("text") : null;
	}

	private String tmp_gpu_ocr_version(byte[] bytes) throws IOException {
		String b64 = Base64.getEncoder().encodeToString(bytes);
		Map<String, Object> p = new HashMap<>();
		p.put("model", conf.getOcrApiLocalGpuModel());
		p.put("messages", List.of(Map.of("role", "user", "content", List.of(Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + b64)), Map.of("type", "text", "text", "Extract only the visible text from the image.\nReturn the extracted text exactly as it appears, line by line.")))));
		p.put("max_tokens", 1500);
		p.put("temperature", 0.0);
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		String payload = JSON.toJSONString(p);
		String url = appendPath(ocrBaseUrl(), "/v1/chat/completions");
		ResponseEntity<String> r = restTemplate.postForEntity(url, new HttpEntity<>(payload, h), String.class);
		if (!r.getStatusCode().is2xxSuccessful()) throw new IOException("HTTP " + r.getStatusCode());
		JSONObject j = JSONObject.parseObject(r.getBody());
		return j != null ? j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content") : null;
	}

	private String synap_ocr_version(InputStream in, String name) throws IOException {
		String url = appendPath(ocrBaseUrl(), "/sdk/ocr");
		Connection.Response r = Jsoup.connect(url).timeout(conf.getOcrTimeoutSec() * 1000).method(Connection.Method.POST).ignoreContentType(true).data("api_key", conf.getOcrApiKey()).data("type", "upload").data("textout", "true").data("boxes_type", "line").data("image", name, in).execute();
		return JSONObject.parseObject(r.body()).getJSONObject("result").getString("full_text");
	}

	private String ocrBaseUrl() {
		return endpointResolver.resolveConfiguredUrl(ocrBaseUrlConfig(), DeviceServiceKey.OCR);
	}

	private String ocrBaseUrlConfig() {
		String host = Common.nvl(conf.getOcrApiHost()).trim();
		if (host.contains("://")) return host;
		if (host.contains(":") || Common.isEmpty(conf.getOcrApiPort())) return "http://" + host;
		return String.format(URL_FMT, host, conf.getOcrApiPort());
	}

	private String appendPath(String baseUrl, String path) {
		if (baseUrl.endsWith("/") && path.startsWith("/")) return baseUrl + path.substring(1);
		if (!baseUrl.endsWith("/") && !path.startsWith("/")) return baseUrl + "/" + path;
		return baseUrl + path;
	}
}
