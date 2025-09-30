package com.xcurenet.common.types;

import com.xcurenet.common.Constants;
import com.xcurenet.common.utils.CommonUtil;
import lombok.Data;

@Data
public class AttachExtension {
	private boolean fileNameExist;  //파일명 탐지 여부
	private String ext;             // 확장자
	public boolean unknown;         // 확장자 알수 없음
	private FILTERTYPE filterType = FILTERTYPE.NONE; // 첨부 분석 방식
	private String desc;            // 설명 drm이 있기도 함.
	private boolean encrypted;      //암호화 여부
	private DRMTYPE drm = DRMTYPE.N;

	public AttachExtension(final String input) {
		if (input == null) return;

		String[] parts = input.split("\\|");
		if (parts.length >= 1 && CommonUtil.isEquals(parts[0], "1")) this.fileNameExist = true;
		if (parts.length >= 2) this.ext = parts[1].toLowerCase();
		if (parts.length >= 3) this.filterType = FILTERTYPE.valueOf(parts[2]);
		if (parts.length >= 4) this.desc = parts[3];
		if (parts.length >= 5 && CommonUtil.isEquals(parts[4], "1")) this.encrypted = true;
		this.unknown = Constants.UNKNOWN.equalsIgnoreCase(this.ext);
	}

	public enum FILTERTYPE { // 첨부 분석 방식
		NONE,
		SYNAP, // 사이냅
		DETECTOR // 파일 헤더 분석
	}

	public enum DRMTYPE {
		S, // success
		F, // fail
		N  // none
	}
}
