package com.xcurenet.common.regex;

import lombok.Builder;
import lombok.Data;

import java.util.regex.Pattern;

@Data
@Builder
public class DetectOptions {
	private String key;
	private String pattern;
	private Pattern compile;
	private int minCount;
}
