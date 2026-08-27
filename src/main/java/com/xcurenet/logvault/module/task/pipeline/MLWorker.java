package com.xcurenet.logvault.module.task.pipeline;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.device.DeviceEndpointResolver;
import com.xcurenet.logvault.device.DeviceServiceKey;
import com.xcurenet.logvault.fs.FileProcessor;
import com.xcurenet.logvault.loader.ImageCategoryLoader;
import com.xcurenet.logvault.module.analysis.AnomalyScoreCalculator;
import com.xcurenet.logvault.module.analysis.GuardRailAnalysis;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.extern.log4j.Log4j2;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

@Log4j2
@Component
public class MLWorker implements PipelineWorker {

	private final Config conf;
	private final RestTemplate restTemplate;
	private final FileProcessor fileProcessor;
	private final IndexService indexService;
	private final GuardRailAnalysis guardRailAnalysis;
	private final AnomalyScoreCalculator anomalyScoreCalculator;
	private final ImageCategoryLoader imageCategoryLoader;
	private final DeviceEndpointResolver endpointResolver;

	public MLWorker(Config conf, @Qualifier("mlRestTemplate") RestTemplate restTemplate, FileProcessor fileProcessor, IndexService indexService, GuardRailAnalysis guardRailAnalysis, AnomalyScoreCalculator anomalyScoreCalculator, ImageCategoryLoader imageCategoryLoader, DeviceEndpointResolver endpointResolver) {
		this.conf = conf;
		this.restTemplate = restTemplate;
		this.fileProcessor = fileProcessor;
		this.indexService = indexService;
		this.guardRailAnalysis = guardRailAnalysis;
		this.anomalyScoreCalculator = anomalyScoreCalculator;
		this.imageCategoryLoader = imageCategoryLoader;
		this.endpointResolver = endpointResolver;
	}

	@Override
	public String getTaskType() {
		return "ML";
	}

	@Override
	public boolean isEnabled() {
		return conf.isMlApiEnable();
	}

	@Override
	public int getWorkerCount() {
		return conf.getTaskQueueMlThreads();
	}

	@Override
	public boolean isTarget(EmassDoc doc) {
		return Common.isEquals(doc.getService().getSvc3(), "S");
	}

	@Override
	public EmassDoc process(EmassDoc doc) throws Exception {
		analyzeBody(doc);
		analyzeAttach(doc);
		analyzeSimilarity(doc);
		analyzeImageSimilarity(doc);
		buildSummary(doc);
		detectGuardRail(doc);
		EmassDoc.ProcessStatus st = doc.getProcessStatus() == null ? EmassDoc.ProcessStatus.builder().build() : doc.getProcessStatus();
		st.setMl("E");
		doc.setProcessStatus(st);
		anomalyScoreCalculator.calculate(doc);
		indexService.index(doc);
		return doc;
	}

	private void analyzeBody(EmassDoc doc) {
		try {
			if (doc.getBody() != null && doc.getBody().getText() != null) {
				StopWatch sw = DateUtils.start();
				EmassDoc.MLResult r = callML(Common.limitLength(doc.getBody().getText(), conf.getMlApiTextLimit()));
				log.info("ML__TASK | BODY: {} | {}", r, DateUtils.stop(sw));
				if (r != null) doc.getBody().setMlResult(r);
			}
		} catch (Exception e) {
			log.warn("{} | {}", ErrorCode.ML_ANALYSIS_BODY_ERROR, e.toString());
		}
	}

	private void analyzeAttach(EmassDoc doc) {
		if (doc.getAttach() == null) return;
		for (EmassDoc.Attach a : doc.getAttach()) {
			if (a.getText() == null) continue;
			String text = Common.limitLength(a.getText(), conf.getMlApiTextLimit());
			apply(a, text, "ML__TASK", this::callML);
		}
	}

	private void analyzeSimilarity(EmassDoc doc) {
		if (!conf.isTrainingDocsEnable()) return;
		try {
			StopWatch sw = DateUtils.start();
			SimilarityResponse response = callSimilarity(doc);
			applySimilarity(doc.getBody(), response.body());
			for (SimilarityAttachResult result : response.attachments()) {
				applySimilarity(result.attach(), result.result());
			}
		} catch (Exception e) {
			log.warn("SIM__ERR | {}", e.getMessage(), e);
		}
	}

	private void detectGuardRail(EmassDoc doc) {
		try {
			guardRailAnalysis.detect(doc);
		} catch (Exception e) {
			log.warn("ML_GUARDRAIL | {}", e.getMessage(), e);
		}
	}

	private void analyzeImageSimilarity(EmassDoc doc) {
		if (doc.getAttach() == null || !conf.isImageSimilarityEnable()) return;
		for (EmassDoc.Attach a : doc.getAttach()) {
			if (a == null) continue;
			analyzeAttachImageSimilarity(a);
			analyzeEmbeddedImageSimilarity(a);
		}
	}

	private void analyzeAttachImageSimilarity(EmassDoc.Attach a) {
		if (a == null || !a.isExist() || !isImageSimilarityTarget(a) || Common.isEmpty(a.getPath())) return;

		try (InputStream in = fileProcessor.open(a.getPath())) {
			StopWatch sw = DateUtils.start();
			ImageSimilarityResponse r = callImageSimilarity(in, fileName(a.getName(), a.getPath()));
			appendImageSimilarity(a, r.imageSimilarity());
			log.info("IMG_SIM | ATTACH:{} | {} | {}", a.getName(), r.imageSimilarity(), DateUtils.stop(sw));
		} catch (Exception e) {
			log.warn("IMG_SIM_WARN | ATTACH:{} | {}", a.getName(), e.getMessage(), e);
		}
	}

	private void analyzeEmbeddedImageSimilarity(EmassDoc.Attach a) {
		if (a == null || a.getImageExtractorInfo() == null || a.getImageExtractorInfo().isEmpty()) return;

		for (EmassDoc.ImageExtractorInfo img : a.getImageExtractorInfo()) {
			if (img == null || Common.isEmpty(img.getPath())) {
				continue;
			}

			try (InputStream in = fileProcessor.open(img.getPath())) {
				StopWatch sw = DateUtils.start();
				ImageSimilarityResponse r = callImageSimilarity(in, fileName(img.getName(), img.getPath()));
				appendImageSimilarity(a, r.imageSimilarity());
				log.info("IMG_SIM | EMBED:{} | {} | {}", img.getName(), r.imageSimilarity(), DateUtils.stop(sw));
			} catch (Exception e) {
				log.warn("IMG_SIM_WARN | EMBED:{} | {}", img.getName(), e.getMessage(), e);
			} finally {
				deleteEmbeddedImage(img);
			}
		}
		a.setImageExtractorInfo(null);
	}

	private void appendImageSimilarity(EmassDoc.Attach attach, EmassDoc.ImageSimilarity imageSimilarity) {
		if (attach == null || imageSimilarity == null || Common.isEmpty(imageSimilarity.getCategoryId())) return;
		if (attach.getImageSimilarity() == null) {
			attach.setImageSimilarity(new ArrayList<>());
		}
		attach.getImageSimilarity().add(imageSimilarity);
	}

	private void deleteEmbeddedImage(EmassDoc.ImageExtractorInfo img) {
		if (img == null || Common.isEmpty(img.getPath())) return;

		try {
			if (fileProcessor.delete(img.getPath())) {
				log.info("IMG_SIM_DELETE | {}", conf.getDestPathSmall(img.getPath()));
			}
		} catch (Exception e) {
			log.warn("IMG_SIM_DELETE_WARN | {} | {}", img.getPath(), e.getMessage(), e);
		}
	}

	private void apply(EmassDoc.Attach a, String text, String prefix, Function<String, EmassDoc.MLResult> fn) {
		try {
			EmassDoc.MLResult r = fn.apply(text);
			log.info("{} | ATTACH: {}", prefix, r);
			if (r == null) return;
			EmassDoc.MLResult cur = a.getMlResult();
			if (cur == null || cur.getResult() > 200) {
				a.setMlResult(r);
				return;
			}
			merge(cur, r);
		} catch (Exception e) {
			log.warn("{} | {}", ErrorCode.ML_ANALYSIS_ATTACH_ERROR, e.toString());
		}
	}

	private void merge(EmassDoc.MLResult t, EmassDoc.MLResult s) {
		t.setCodeExist(t.isCodeExist() || s.isCodeExist());
		t.setCategory(Math.max(t.getCategory(), s.getCategory()));
		t.setProbs(Math.max(t.getProbs(), s.getProbs()));
		if (s.getKeywords() != null && !s.getKeywords().isEmpty()) {
			if (t.getKeywords() == null) t.setKeywords(new ArrayList<>());
			t.getKeywords().addAll(s.getKeywords());
		}
		if (s.isSimilarityExist()
				&& (!t.isSimilarityExist() || s.getSimilarityScore() > t.getSimilarityScore())) {
			t.setSimilarityExist(true);
			t.setSimilarityId(s.getSimilarityId());
			t.setSimilarityName(s.getSimilarityName());
			t.setSimilarityScore(s.getSimilarityScore());
		}
		if (s.getResult() > 0 && (t.getResult() <= 0 || s.getResult() > t.getResult())) t.setResult(s.getResult());
		if (s.getMessage() != null && !s.getMessage().isBlank()) t.setMessage(s.getMessage());
	}

	private void buildSummary(EmassDoc doc) {
		EmassDoc.MLResult sm = new EmassDoc.MLResult();
		int result = 200;
		String msg = "OK";
		if (doc.getBody() != null && doc.getBody().getMlResult() != null) {
			sm.merge(doc.getBody().getMlResult());
			result = doc.getBody().getMlResult().getResult();
			msg = doc.getBody().getMlResult().getMessage();
		}
		if (doc.getAttach() != null) for (EmassDoc.Attach a : doc.getAttach()) {
			if (a.getMlResult() == null) continue;
			sm.merge(a.getMlResult());
			if (a.getMlResult().getResult() > 200) {
				result = a.getMlResult().getResult();
				msg = a.getMlResult().getMessage();
			}
		}
		sm.setResult(result);
		sm.setMessage(msg);
		doc.setMlResult(sm);
	}

	private EmassDoc.MLResult callML(String text) {
		JSONObject d = new JSONObject();
		d.put("text", Common.toBase64(text.getBytes()));
		d.put("code_split_threshold", conf.getMlCodeSplitThreshold());
		d.put("codeline_exist_threshold", conf.getMlCodelineExistThreshold());
		d.put("detect_model_dir", conf.getMlDetectModelDir());
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		h.setAccept(List.of(MediaType.APPLICATION_JSON));
		String payload = d.toJSONString();
		String url = endpointResolver.resolveConfiguredUrl(conf.getMlApiUrl(), DeviceServiceKey.DA);
		ResponseEntity<String> r = restTemplate.postForEntity(url, new HttpEntity<>(payload, h), String.class);
		if (!r.getStatusCode().is2xxSuccessful()) {
			log.warn("ML_ERR | {} | {}", url, r.getStatusCode());
			return null;
		}
		JSONObject b = JSONObject.parseObject(r.getBody());
		if (b == null) return null;
		EmassDoc.MLResult m = new EmassDoc.MLResult();
		m.setCodeExist(b.getBoolean("code_exist"));
		m.setProbs(b.getFloat("probs"));
		m.setCategory(b.getInteger("class"));
		m.setKeywords(b.getJSONArray("keywords").toJavaList(String.class));
		m.setResult(b.getInteger("result"));
		m.setMessage(b.getString("message"));
		return m;
	}

	private SimilarityResponse callSimilarity(EmassDoc doc) throws IOException {
		MultiValueMap<String, Object> payload = new LinkedMultiValueMap<>();
		String bodyText = doc.getBody() == null ? "" : Common.nvl(doc.getBody().getText());
		payload.add("body", bodyText);

		List<EmassDoc.Attach> sentAttachments = new ArrayList<>();
		List<String> requestAttachmentSummary = new ArrayList<>();
		if (doc.getAttach() != null) {
			for (EmassDoc.Attach attach : doc.getAttach()) {
				if (attach == null || !attach.isExist() || Common.isEmpty(attach.getPath())
						|| !isTrainingDocsTarget(attach)) continue;
				try (InputStream in = fileProcessor.open(attach.getPath())) {
					byte[] bytes = in.readAllBytes();
					String uploadFileName = trainingDocsFileName(attach);
					ByteArrayResource resource = new ByteArrayResource(bytes) {
						@Override
						public String getFilename() {
							return uploadFileName;
						}
					};
					payload.add("attachments", resource);
					sentAttachments.add(attach);
					requestAttachmentSummary.add(String.format("%d:%s(%s)", sentAttachments.size() - 1,
							resource.getFilename(), Common.convertFileSize(bytes.length)));
				} catch (Exception e) {
					log.warn("SIM_SKIP | {} | {}", attach.getName(), e.getMessage());
				}
			}
		}
		if (bodyText.isBlank() && sentAttachments.isEmpty()) {
			log.info("SIM_SKIP | NO_CONTENT | BODY_LEN:{} | ATTACH_COUNT:{}", bodyText.length(), sentAttachments.size());
			return new SimilarityResponse(null, List.of());
		}

		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.MULTIPART_FORM_DATA);
		h.setAccept(List.of(MediaType.APPLICATION_JSON));
		String url = trainingDocsAnalyzeContentUrl();
		log.info("SIM__REQ | BODY_LEN:{} | ATTACH_COUNT:{} | ATTACHMENTS:{}",
				bodyText.length(), sentAttachments.size(), requestAttachmentSummary);
		ResponseEntity<String> r = restTemplate.postForEntity(url, new HttpEntity<>(payload, h), String.class);
		if (!r.getStatusCode().is2xxSuccessful()) {
			throw new IOException("HTTP " + r.getStatusCode());
		}
		JSONObject b = JSONObject.parseObject(r.getBody());
		if (b == null) throw new IOException("empty response");

		EmassDoc.MLResult bodyResult = similarityResult(b.getJSONObject("body"));
		List<SimilarityAttachResult> attachmentResults = new ArrayList<>();
		List<String> responseAttachmentSummary = new ArrayList<>();
		JSONArray attachments = b.getJSONArray("attachments");
		if (attachments != null) for (Object value : attachments) {
			JSONObject item = (JSONObject) value;
			int index = item.getIntValue("attach_index", -1);
			EmassDoc.MLResult result = similarityResult(item);
			if (index >= 0 && index < sentAttachments.size()) {
				EmassDoc.Attach attach = sentAttachments.get(index);
				attachmentResults.add(new SimilarityAttachResult(attach, result));
				responseAttachmentSummary.add(String.format("%d:%s[%s]", index,
						trainingDocsFileName(attach), similaritySummary(result)));
			} else {
				responseAttachmentSummary.add(String.format("%d:UNKNOWN[%s]", index, similaritySummary(result)));
			}
		}
		log.info("SIM__RES | BODY:[{}] | ATTACHMENTS:{}", similaritySummary(bodyResult), responseAttachmentSummary);
		if (!b.getBooleanValue("success")) throw new IOException(Common.nvl(b.getString("message")));
		return new SimilarityResponse(bodyResult, attachmentResults);
	}

	private String similaritySummary(EmassDoc.MLResult result) {
		if (result == null || !result.isSimilarityExist()) return "NONE";
		return String.format("ID:%s,NAME:%s,SCORE:%s", result.getSimilarityId(),
				result.getSimilarityName(), result.getSimilarityScore());
	}

	private EmassDoc.MLResult similarityResult(JSONObject item) {
		if (item == null) return null;
		JSONArray matches = item.getJSONArray("matches");
		if (matches == null || matches.isEmpty()) return null;
		JSONObject best = null;
		for (Object value : matches) {
			JSONObject match = (JSONObject) value;
			if (best == null || match.getFloatValue("score_percent") > best.getFloatValue("score_percent")) best = match;
		}
		if (best == null) return null;
		EmassDoc.MLResult m = new EmassDoc.MLResult();
		m.setSimilarityExist(true);
		m.setSimilarityId(best.getString("document_id"));
		m.setSimilarityScore(best.getFloatValue("score_percent"));
		m.setSimilarityName(best.getString("document_title"));
		return m;
	}

	private void applySimilarity(EmassDoc.Body body, EmassDoc.MLResult result) {
		if (body == null || result == null) return;
		if (body.getMlResult() == null) body.setMlResult(result);
		else merge(body.getMlResult(), result);
	}

	private void applySimilarity(EmassDoc.Attach attach, EmassDoc.MLResult result) {
		if (attach == null || result == null) return;
		if (attach.getMlResult() == null) attach.setMlResult(result);
		else merge(attach.getMlResult(), result);
	}

	private String trainingDocsAnalyzeContentUrl() {
		String host = endpointResolver.resolveConfiguredUrl(conf.getTrainingDocsApiHost(), DeviceServiceKey.DA);
		String path = Common.nvl(conf.getTrainingDocsApiAnalyzeContent()).trim();
		if (host.endsWith("/") && path.startsWith("/")) return host + path.substring(1);
		if (!host.endsWith("/") && !path.startsWith("/")) return host + "/" + path;
		return host + path;
	}

	private boolean isTrainingDocsTarget(EmassDoc.Attach attach) {
		return isTrainingDocsTargetExt(attach.getExtension())
				|| isTrainingDocsTargetExt(attach.getExpectedExtension());
	}

	private boolean isTrainingDocsTargetExt(String value) {
		Set<String> targetExts = conf.getTrainingDocsFileExts();
		if (targetExts == null || targetExts.isEmpty()) return false;

		String ext = normalizeExt(value);
		if (Common.isEmpty(ext)) return false;

		for (String targetExt : targetExts) {
			if (ext.equals(normalizeExt(targetExt))) return true;
		}
		return false;
	}

	private String trainingDocsFileName(EmassDoc.Attach attach) {
		String name = fileName(attach.getName(), attach.getPath());
		if (isTrainingDocsTargetExt(attach.getExtension())) return name;

		String expectedExt = normalizeExt(attach.getExpectedExtension());
		if (isTrainingDocsTargetExt(expectedExt)) return name + "." + expectedExt;
		return name;
	}

	private ImageSimilarityResponse callImageSimilarity(InputStream in, String name) throws IOException {
		byte[] bytes = in.readAllBytes();
		return callImageSimilarity(imageSimilarityDetectUrl(), bytes, name);
	}

	private ImageSimilarityResponse callImageSimilarity(String url, byte[] bytes, String name) throws IOException {
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
		if (!r.getStatusCode().is2xxSuccessful()) {
			throw new IOException("HTTP " + r.getStatusCode());
		}

		JSONObject b = JSONObject.parseObject(r.getBody());
		if (b == null) throw new IOException("empty response");

		Object error = b.get("error");
		if (error != null) {
			throw new IOException(String.valueOf(error));
		}

		JSONObject verdict = b.getJSONObject("verdict");
		if (verdict == null) throw new IOException("missing verdict");

		String category = verdict.getString("final_category");
		EmassDoc.ImageSimilarity result = Common.isEmpty(category) ? null : EmassDoc.ImageSimilarity.builder()
				.categoryId(category)
				.categoryName(imageCategoryLoader.getCategoryName(category))
				.riskScore(verdict.getInteger("final_risk_score"))
				.confidence(verdict.getFloat("confidence"))
				.build();
		log.info("IMG__RES | FILE:{} | SIZE:{} | CATEGORY:{} | RISK:{} | CONFIDENCE:{}",
				name, Common.convertFileSize(bytes.length),
				result == null ? null : result.getCategoryId(), result == null ? null : result.getRiskScore(),
				result == null ? null : result.getConfidence());
		return new ImageSimilarityResponse(result);
	}

	private String imageSimilarityDetectUrl() {
		String detect = Common.nvl(conf.getImageSimilarityApiDetect()).trim();
		String host = endpointResolver.resolveConfiguredUrl(conf.getImageSimilarityApiHost(), DeviceServiceKey.IMAGE_SIMILARITY);
		return imageSimilarityDetectUrl(host, detect);
	}

	private String imageSimilarityDetectUrl(String host, String detect) {
		if (host.endsWith("/") && detect.startsWith("/")) return host + detect.substring(1);
		if (!host.endsWith("/") && !detect.startsWith("/")) return host + "/" + detect;
		return host + detect;
	}

	private boolean isImageSimilarityTarget(EmassDoc.Attach a) {
		return isImageSimilarityTargetExt(a.getExtension()) || isImageSimilarityTargetExt(a.getExpectedExtension());
	}

	private boolean isImageSimilarityTargetExt(String value) {
		Set<String> targetExts = conf.getImageSimilarityTargetExt();
		if (targetExts == null || targetExts.isEmpty()) return false;

		String ext = normalizeExt(value);
		if (Common.isEmpty(ext)) return false;

		for (String targetExt : targetExts) {
			if (ext.equals(normalizeExt(targetExt))) return true;
		}
		return false;
	}

	private String normalizeExt(String value) {
		String ext = Common.nvl(value).trim().toLowerCase(Locale.ROOT);
		int slash = Math.max(ext.lastIndexOf('/'), ext.lastIndexOf('\\'));
		if (slash >= 0 && slash < ext.length() - 1) {
			ext = ext.substring(slash + 1);
		}
		int dot = ext.lastIndexOf('.');
		if (dot >= 0 && dot < ext.length() - 1) {
			ext = ext.substring(dot + 1);
		}
		return ext;
	}

	private String fileName(String name, String path) {
		if (Common.isNotEmpty(name)) return name;

		String fileName = Common.nvl(path);
		int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
		if (slash >= 0 && slash < fileName.length() - 1) {
			fileName = fileName.substring(slash + 1);
		}
		return Common.isEmpty(fileName) ? "image" : fileName;
	}

	private record ImageSimilarityResponse(EmassDoc.ImageSimilarity imageSimilarity) {
	}

	private record SimilarityResponse(EmassDoc.MLResult body, List<SimilarityAttachResult> attachments) {
	}

	private record SimilarityAttachResult(EmassDoc.Attach attach, EmassDoc.MLResult result) {
	}
}
