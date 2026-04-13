package com.xcurenet.logvault.module.task.pipeline;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.utils.Common;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Log4j2
@Component
public class MLWorker implements PipelineWorker {

	private final Config conf;
	private final RestTemplate restTemplate;
	private final IndexService indexService;

	public MLWorker(Config conf, @Qualifier("mlRestTemplate") RestTemplate restTemplate, IndexService indexService) {
		this.conf = conf;
		this.restTemplate = restTemplate;
		this.indexService = indexService;
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
		buildSummary(doc);
		EmassDoc.ProcessStatus st = doc.getProcessStatus() == null ? EmassDoc.ProcessStatus.builder().build() : doc.getProcessStatus();
		st.setMl("E");
		doc.setProcessStatus(st);
		indexService.index(doc);
		return doc;
	}

	private void analyzeBody(EmassDoc doc) {
		try {
			if (doc.getBody() != null && doc.getBody().getText() != null) {
				EmassDoc.MLResult r = callML(Common.limitLength(doc.getBody().getText(), conf.getMlApiTextLimit()));
				log.info("ML__TASK | BODY: {}", r);
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
}
