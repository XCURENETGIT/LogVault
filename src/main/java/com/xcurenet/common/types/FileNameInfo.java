package com.xcurenet.common.types;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.io.Serializable;

@Log4j2
@Data
public class FileNameInfo implements Serializable {

	@Serial
	private static final long serialVersionUID = -6888540790595666821L;

	private String prefix;
	private String ctime;
	private IP srcIP;
	private IP dstIP;
	private int srcPort;
	private int dstPort;
	private int seq = 0;
	private String cid = "0";
	private String deviceName;
	private String decodeHost;
	private String suffix;

	public static FileNameInfo getInfo(final String filePath) throws Exception {
		final FileNameInfo info = new FileNameInfo();
		final String filename = FilenameUtils.getName(filePath);
		final String[] dotSplit = filename.split("\\.", 2);
		final String[] split = dotSplit[0].split("-", 10);
		final int startOffset = split[0].indexOf("2");
		if (startOffset > 0) info.prefix = split[0].substring(0, startOffset);
		info.ctime = split[0].substring(startOffset);
		info.srcIP = new IP(split[1]);
		info.dstIP = new IP(split[2]);
		info.srcPort = Integer.parseInt(split[3]);
		info.dstPort = Integer.parseInt(split[4]);

		if (split.length > 5) {
			try {
				info.seq = Integer.parseInt(split[5]);
			} catch (final NumberFormatException e) {
				// FMT 파일이 파일 네이밍 규칙에서 어긋난다. 예외처리
			}
		}

		// 수집 장비에 따라 CID, DEVIANCE 없는 경우가 있다.
		if (split.length > 6) info.cid = split[6];
		if (split.length > 7) info.deviceName = split[7];
		if (split.length > 8) info.decodeHost = split[8];

		final StringBuilder sb = new StringBuilder();
		if (split.length > 8) {
			sb.append("-").append(split[8]);
			if (split.length > 9) { // 딜리미터 10개 이후는 하나로 합쳐서 넣는다.
				sb.append("-").append(split[9]);
			}
		}
		if (dotSplit.length > 1) sb.append(".").append(dotSplit[1]);
		info.suffix = sb.toString();
		return info;
	}

	public String getName() {
		final StringBuilder sb = new StringBuilder();
		if (this.prefix != null) sb.append(this.prefix);
		final String[] infos = new String[]{this.ctime, this.srcIP.toHexString(), this.dstIP.toHexString(), Integer.toString(this.srcPort), Integer.toString(this.dstPort), Integer.toString(this.seq), this.cid,};
		sb.append(StringUtils.join(infos, '-'));
		if (this.deviceName != null) sb.append("-").append(this.deviceName);
		if (this.suffix != null) sb.append(this.suffix);
		return sb.toString();
	}
}