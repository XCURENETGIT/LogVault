package com.xcurenet.common.utils;

import org.apache.commons.mail.util.MimeMessageParser;
import org.apache.commons.mail.util.MimeMessageUtils;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

public class EMLParser {
	private EMLParser() {
		throw new IllegalStateException("Utility class");
	}

	private static final Properties props = System.getProperties();
	private static final Session session;

	static {
		props.setProperty("mail.mime.base64.ignoreerrors", "true");
		props.setProperty("mail.mime.multipart.ignoreexistingboundaryparameter", "true");
		session = Session.getDefaultInstance(props, null);
	}

	public static String parse(final byte[] message) throws UnsupportedEncodingException {
		return parse(message, "UTF8");
	}

	public static String parse(final byte[] message, final String charset) throws UnsupportedEncodingException {
		try {
			final MimeMessage msg = MimeMessageUtils.createMimeMessage(session, message);
			if (msg.getHeader("MIME-Version") != null || !msg.isMimeType("text/*")) {
				final MimeMessageParser parser = new MimeMessageParser(msg).parse();
				if (parser.hasPlainContent()) {
					return parser.getPlainContent();
				} else if (parser.hasHtmlContent()) {
					return parser.getHtmlContent();
				}
			}
		} catch (final Exception e) {
			// IGNORE Exceptions
		}
		return new String(message, charset);
	}
}
