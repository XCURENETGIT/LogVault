package com.xcurenet.common.types;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.regex.Pattern;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import com.xcurenet.common.utils.CommonUtil;

public class IP implements Serializable {
	@Serial
	private static final long serialVersionUID = 7377908637131394231L;

	private static final Pattern HEX_REGEX = Pattern.compile("^[0-9a-fA-F]{1,32}$");
	@Getter
	private boolean isIPv6;
	private byte[] addr;
	private String canonicalAddr;
	private String rawAddr;
	private String hexAddr;
	private Long lAddr;

	public IP(final String ip) throws IOException {
		if (HEX_REGEX.matcher(ip).find()) {
			// Hex 값일 것이다.
			byte[] b = CommonUtil.hexToBytes(ip);
			if (b.length < 4) {
				// 4 바이트 미만인 경우 패딩 추가
				final byte[] buf = new byte[4];
				System.arraycopy(b, 0, buf, 4 - b.length, b.length);
				b = buf;
			}
			setInetAddress(InetAddress.getByAddress(b), ip);
		} else {
			setInetAddress(InetAddress.getByName(ip), ip);
		}
	}

	public IP(final byte[] ip) throws IOException {
		setInetAddress(InetAddress.getByAddress(ip), CommonUtil.toHexString(ip));
	}

	public IP(final InetAddress inetAddress) {
		setInetAddress(inetAddress, inetAddress.getHostAddress());
	}

	public static IP create(final long ip) throws IOException {
		return new IP(CommonUtil.inet_ltoa(ip));
	}

	public static IP create(final String ip) throws IOException {
		return StringUtils.isEmpty(ip) ? null : new IP(ip);
	}

	private void setInetAddress(final InetAddress inetAddress, final String rawAddr) {
		this.isIPv6 = inetAddress instanceof Inet6Address;
		this.addr = inetAddress.getAddress();
		this.canonicalAddr = inetAddress.getHostAddress();
		this.rawAddr = rawAddr;
	}

	public boolean isIPv4() {
		return !isIPv6;
	}

	public String toHexString() {
		if (hexAddr == null) {
			hexAddr = CommonUtil.toHexString(addr);
		}
		return hexAddr;
	}

	// IPV4 Only
	public long toLong() {
		if (lAddr == null) {
			if (isIPv6()) {
				throw new RuntimeException("IPv4 address only");
			}
			lAddr = CommonUtil.inet_btol(toBytes());
		}
		return lAddr;
	}

	public byte[] toBytes() {
		return addr;
	}

	public String toCanonicalAddr() {
		return canonicalAddr;
	}

	public String toRawAddr() {
		return rawAddr;
	}

	@Override
	public String toString() {
		return toCanonicalAddr();
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(addr);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof IP)) {
			return false;
		}
		return Arrays.equals(addr, ((IP) obj).toBytes());
	}
}