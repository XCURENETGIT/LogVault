package com.xcurenet.common.fileanalysis.service.extension;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log4j2
@Getter
@Component
@PropertySource(value = {"file:/users/logvault/conf/extension.properties", "classpath:extension.properties"}, ignoreResourceNotFound = true, encoding = "UTF-8")
@ConfigurationProperties(prefix = "extension")
public class FileExtensionConfig {
	private Map<String, String> mapping = new HashMap<>();
	private final Map<String, List<String>> mappingMap = new HashMap<>();

	private Map<String, String> splitExt = new HashMap<>();
	private Map<String, String> splitName = new HashMap<>();
	private final Map<String, List<Pattern>> splitExtPatterns = new HashMap<>();
	private final Map<String, List<Pattern>> splitNamePatterns = new HashMap<>();

	public void setMapping(Map<String, String> mapping) {
		this.mapping = mapping;
		convertToListMap();

		log.debug("FILE_EXT | mapping load success : {}", mappingMap);
	}

	// setter 추가: properties의 extension.split.ext.*, extension.split.name.* 바인딩
	public void setSplitExt(Map<String, String> splitExt) {
		this.splitExt = splitExt != null ? splitExt : new HashMap<>();
		compileSplitPatterns(this.splitExt, this.splitExtPatterns);

		log.debug("SPLIT_EXT | load success : {}", this.splitExtPatterns);
	}

	public void setSplitName(Map<String, String> splitName) {
		this.splitName = splitName != null ? splitName : new HashMap<>();
		compileSplitPatterns(this.splitName, this.splitNamePatterns);

		log.debug("SPLIT_NAME | load success : {}", this.splitNamePatterns);
	}

	private void convertToListMap() {
		mappingMap.clear();
		for (Map.Entry<String, String> entry : mapping.entrySet()) {
			List<String> values = Arrays.stream(entry.getValue().split(",")).map(v -> v.trim().toLowerCase()).collect(Collectors.toList());
			mappingMap.put(entry.getKey().toLowerCase(), values);
		}
	}

	private void compileSplitPatterns(Map<String, String> src, Map<String, List<Pattern>> dest) {
		dest.clear();
		src.forEach((canonical, csv) -> {
			List<Pattern> patterns = Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Pattern::compile).collect(Collectors.toList());
			dest.put(canonical.toLowerCase(), patterns);
		});
	}
}