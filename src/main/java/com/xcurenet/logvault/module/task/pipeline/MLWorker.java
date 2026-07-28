package com.xcurenet.logvault.module.task.pipeline;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
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

	public MLWorker(Config conf, @Qualifier("mlRestTemplate") RestTemplate restTemplate, FileProcessor fileProcessor, IndexService indexService, GuardRailAnalysis guardRailAnalysis, AnomalyScoreCalculator anomalyScoreCalculator, ImageCategoryLoader imageCategoryLoader) {
		this.conf = conf;
		this.restTemplate = restTemplate;
		this.fileProcessor = fileProcessor;
		this.indexService = indexService;
		this.guardRailAnalysis = guardRailAnalysis;
		this.anomalyScoreCalculator = anomalyScoreCalculator;
		this.imageCategoryLoader = imageCategoryLoader;
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
			apply(a, text, "SIMILARITY", this::callSimilarity);
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
		if (s.isSimilarityExist()) t.setSimilarityExist(true);
		if (s.getSimilarityId() != null) t.setSimilarityId(s.getSimilarityId());
		if (s.getSimilarityName() != null) t.setSimilarityName(s.getSimilarityName());
		if (s.getSimilarityScore() > 0f) t.setSimilarityScore(Math.max(t.getSimilarityScore(), s.getSimilarityScore()));
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
		ResponseEntity<String> r = restTemplate.postForEntity(conf.getMlApiUrl(), new HttpEntity<>(d.toJSONString(), h), String.class);
		if (!r.getStatusCode().is2xxSuccessful()) {
			log.warn("ML_ERR | {}", r.getStatusCode());
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

	private EmassDoc.MLResult callSimilarity(String text) {
		JSONObject d = new JSONObject();
		d.put("query", text);
		d.put("top_k", 1);
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		h.setAccept(List.of(MediaType.APPLICATION_JSON));
		h.add("x-api-key", conf.getSimilarityKey());
		ResponseEntity<String> r = restTemplate.postForEntity(conf.getSimilarityUrl(), new HttpEntity<>(d.toJSONString(), h), String.class);
		if (!r.getStatusCode().is2xxSuccessful()) {
			log.warn("SIM_ERR | {}", r.getStatusCode());
			return null;
		}
		JSONObject b = JSONObject.parseObject(r.getBody());
		if (b == null) return null;
		JSONArray res = b.getJSONArray("result");
		if (res == null || res.isEmpty()) return null;
		JSONObject o = res.getJSONObject(0);
		EmassDoc.MLResult m = new EmassDoc.MLResult();
		m.setSimilarityExist(true);
		m.setSimilarityId(o.getString("doc_id"));
		m.setSimilarityScore(o.getFloatValue("score"));
		m.setSimilarityName(o.getJSONObject("metadata").getString("file_name"));
		return m;
	}

	private ImageSimilarityResponse callImageSimilarity(InputStream in, String name) throws IOException {
		ByteArrayResource res = new ByteArrayResource(in.readAllBytes()) {
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

		ResponseEntity<String> r = restTemplate.postForEntity(imageSimilarityDetectUrl(), new HttpEntity<>(body, h), String.class);
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
		if (Common.isEmpty(category)) return new ImageSimilarityResponse(null);

		return new ImageSimilarityResponse(EmassDoc.ImageSimilarity.builder()
				.categoryId(category)
				.categoryName(imageCategoryLoader.getCategoryName(category))
				.riskScore(verdict.getInteger("final_risk_score"))
				.confidence(verdict.getFloat("confidence"))
				.build());
	}

	private String imageSimilarityDetectUrl() {
		String host = Common.nvl(conf.getImageSimilarityApiHost()).trim();
		String detect = Common.nvl(conf.getImageSimilarityApiDetect()).trim();
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
}
