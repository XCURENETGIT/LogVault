package com.xcurenet.common.types;

import com.xcurenet.common.utils.Common;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.util.mime.MimeUtility;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.*;
import java.util.*;

@Data
@Log4j2
public class EMail implements Serializable {
	@Serial
	private static final long serialVersionUID = 248730910187819901L;
	private static final byte[] NULL = new byte[]{0x00, 0x00};
	private static final String EMPTY = "";
	private String name = EMPTY;
	private String emailAddr = EMPTY;
	private boolean isEmail = false;

	public EMail(final String name, final String email) {
		String decodeName = null;
		if (name != null) {
			decodeName = name.replaceAll("^'|'$", EMPTY);
			try {
				decodeName = MimeUtility.decodeText(decodeName);
			} catch (final UnsupportedEncodingException e) {
				log.error("", e);
			}
			this.setName(decodeName);
		}
		this.setEmailAddr(email != null ? email : Objects.requireNonNull(decodeName));
	}

	public static EMail parse(final String addr) {
		if (addr == null) return null;

		// 쓰레기 값이 붙어서 들어오는 경우가 많다.
		final String cleanAddr = StringUtils.substringBefore(StringUtils.substringBefore(checkAddr(addr), ";"), "\uFFFD").trim();
		if (cleanAddr.isEmpty()) return new EMail("", "");
		try {
			final InternetAddress[] emailAddrs = InternetAddress.parseHeader(cleanAddr, false);
			if (emailAddrs.length > 0) {
				final EMail email = new EMail(emailAddrs[0].getPersonal(), emailAddrs[0].getAddress());
				if (email.getEmailAddr() == null) {
					return null;
				} else if (!email.getEmailAddr().contains("@")) {
					email.setEmail(false);
					return email;
				} else {
					email.setEmail(true);
					return email;
				}
			}
		} catch (final AddressException e) {
			log.debug(e.getMessage(), e);
		} catch (Exception e) {
			log.error("", e);
		}
		return new EMail(cleanAddr, cleanAddr);
	}

	public static String checkAddr(final String addr) {
		// " 이 하나만 들어온 경우, mail parse --ex) " <aaaaa@naver.com> => aaaaa@naver.com
		if (addr.length() - addr.replace("\"", "").length() == 1) {
			return addr.substring(addr.indexOf("<") + 1, addr.lastIndexOf(">"));
		}

		// \"이 붙어서 들어온 경우, ex) \"minus\"<aaaa@naver.com> => minus<aaaa@naver.com>
		if (addr.contains("\\\"")) {
			return addr.replace("\\\"", "");
		}
		return addr;
	}

	public static List<EMail> parse(final Collection<String> addrs) throws Exception {
		final List<EMail> emails = new ArrayList<>();
		if (addrs != null) {
			for (final String addr : addrs) {
				final EMail email = parse(addr);
				if (email != null) {
					emails.add(email);
				}
			}
		}
		return emails;
	}

	public static List<EMail> parse(final String[] addrs) throws Exception {
		return parse(Arrays.asList(addrs));
	}

	private static List<EMail> parse(final byte[] b) {
		final List<EMail> emails = new ArrayList<>();
		if (b != null && b.length >= 2) {
			int offset = 0;
			final int size = Common.toShort(b, offset);
			offset += 2;
			for (int i = 0; i < size; i++) {
				String email = null;
				String rep = null;
				int len = Common.toShort(b, offset);
				if (len > 0) {
					email = Objects.requireNonNull(Common.toString(b, offset + 2, len)).trim();
				}
				offset += 2 + len;
				len = Common.toShort(b, offset);
				if (len > 0) {
					rep = Objects.requireNonNull(Common.toString(b, offset + 2, len)).trim();
				}
				offset += 2 + len;

				if (rep != null && !rep.isEmpty()) {
					Common.add(emails, parse(rep));
				} else if (email != null && !email.isEmpty()) {
					Common.add(emails, parse(email));
				}
			}
		}
		return emails;
	}

	@SuppressWarnings("unchecked")
	public static List<EMail> parse(final Object obj) throws Exception {
		List<EMail> emails = null;
		if (obj != null) {
			if (obj instanceof Collection) {
				emails = parse((Collection<String>) obj);
			} else if (obj instanceof String[]) {
				emails = parse((String[]) obj);
			} else if (obj instanceof byte[]) {
				emails = parse((byte[]) obj);
			} else {
				emails = new ArrayList<>();
				final EMail email = parse((String) obj);
				if (email != null) {
					emails.add(email);
				}
			}
		}
		return emails;
	}

	public void setName(final String name) {
		this.name = name.length() > 254 ? name.substring(0, 254) : name;
	}

	public String getAccount() {
		String[] parts = emailAddr.split("@");

		if (parts.length > 0) {
			return parts[0];
		} else {
			return emailAddr;
		}
	}

	public void setEmailAddr(final String email) {
		this.emailAddr = email.length() > 254 ? email.substring(0, 254) : email;
	}

	@Override
	public String toString() {
		return !getName().isEmpty() ? String.format("%s <%s>", getName(), getEmailAddr()) : getEmailAddr();
	}

	public byte[] toBytes() {
		return Common.add(Common.toSizeBytes(getEmailAddr(), 2), Common.toSizeBytes(toString(), 2));
	}

	public static byte[] toBytes(final EMail email) {
		return email != null ? email.toBytes() : NULL;
	}

	public static byte[] toBytes(final List<EMail> emails) {
		if (emails == null || emails.isEmpty()) {
			return null;
		}

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(Common.toBytes(emails.size(), 2));
			for (final EMail email : emails) {
				baos.write(email.toBytes());
			}
		} catch (final IOException e) {
			log.error("", e);
		}
		return baos.toByteArray();
	}
}
