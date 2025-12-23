package com.xcurenet.logvault.module.statics;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatDTO {
	private long total;
	private long prompt;
	private long attach;
}
