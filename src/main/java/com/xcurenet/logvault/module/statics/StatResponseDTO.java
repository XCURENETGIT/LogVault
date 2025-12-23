package com.xcurenet.logvault.module.statics;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class StatResponseDTO {
	private StatDTO total;
	private Map<String, StatDTO> byUser;
}