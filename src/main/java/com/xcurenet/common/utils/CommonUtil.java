package com.xcurenet.common.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.base.Preconditions;
import com.xcurenet.common.Constants;
import com.xcurenet.common.io.LimitedBufferedReader;
import com.xcurenet.common.types.EMail;
import com.xcurenet.common.types.IP;
import com.xcurenet.crypto.Crypto;
import com.xcurenet.crypto.Crypto.CIPHER;
import com.xcurenet.crypto.CryptoInputStream;
import com.xcurenet.crypto.CryptoOutputStream;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.jsoup.Jsoup;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

@Log4j2
public final class CommonUtil {

	private CommonUtil() {
		throw new IllegalStateException("Utility class");
	}

	public static final String EMPTY = "";
	public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
	public static final int SIZEOF_SHORT = Short.SIZE / Byte.SIZE;
	public static final int PRIME = 0x811C9DC5; // FNV-1 Hash
	private static final char[] HEXARRAY = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
	private static final char[] HEXARRAY_LOWER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

	public static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// IP Convert
	public static long inet_atol(final String ip) {
		final String[] ips = ip.split("\\.");
		return (Long.parseLong(ips[0]) << 24) + (Long.parseLong(ips[1]) << 16) + (Long.parseLong(ips[2]) << 8) + Long.parseLong(ips[3]);
	}

	public static String inet_ltoa(final long ip) {
		return String.format("%d.%d.%d.%d", (ip >> 24) & 0xFF, (ip >> 16) & 0xFF, (ip >> 8) & 0xFF, ip & 0xFF);
	}

	public static String inet_btoa(final byte[] ip) {
		return String.format("%d.%d.%d.%d", ip[0] & 0xFF, ip[1] & 0xFF, ip[2] & 0xFF, ip[3] & 0xFF);
	}

	public static long inet_btol(final byte[] ip) {
		return ((long) (ip[0] & 0xFF) << 24) + ((long) (ip[1] & 0xFF) << 16) + ((long) (ip[2] & 0xFF) << 8) + (ip[3] & 0xFF);
	}

	public static long inet_htol(final String ip) {
		return Long.parseLong(ip, 16);
	}

	public static String inet_htoa(final String ip) {
		return inet_ltoa(inet_htol(ip));
	}

	public static String inet_atoh(final String ip) {
		return Long.toString(inet_atol(ip), 16);
	}

	// FNV Hash
	public static long fnvHash(final String str, final int len) {
		long hash = 0;
		for (int i = 0; i < len; i++) {
			hash = (hash * PRIME) ^ str.charAt(i);
		}
		return hash & 0xFFFFFFFFL;
	}

	public static long fnvHash(final String str) {
		return fnvHash(str, str.length());
	}

	public static long getSplitNum(final String file, final int splitCnt) {
		final int pos = file.indexOf('.');
		final int len = (pos != -1 ? pos : file.length());
		final long hash = fnvHash(file, len);
		return hash % splitCnt;
	}

	public static String makeFilepath(final String... paths) {
		final String join = StringUtils.join(paths, '/');
		if (join == null) {
			return null;
		}
		return FilenameUtils.normalize(join.replaceAll("/+", "/"), true);
	}

	public static String getKeepParentDir(final File file, final int depth) {
		if (depth == 0) {
			return "";
		}

		final Deque<String> deque = new ArrayDeque<>();
		File p = file;
		for (int i = 0; i < depth; i++) {
			p = p.getParentFile();
			if (p == null) {
				break;
			}
			deque.addFirst(p.getName());
		}

		final String[] array = deque.toArray(new String[0]);
		return makeFilepath(array);
	}

	public static byte[] toBytes(final String s) {
		if (s != null) {
			return s.getBytes(StandardCharsets.UTF_8);
		}
		return new byte[0];
	}

	public static byte[] toSizeBytes(final byte[] b, final int length) {
		return b != null ? add(toBytes(b.length, length), b) : null;
	}

	public static byte[] toSizeBytes(final String s, final int length) {
		return toSizeBytes(toBytes(s), length);
	}

	public static String toString(final byte[] b) {
		return b != null ? toString(b, 0, b.length) : null;
	}

	public static String toString(final byte[] b, final int offset) {
		return b != null ? toString(b, offset, b.length - offset) : null;
	}

	public static String toString(final byte[] b, final int offset, final int len) {
		if (b != null && b.length >= offset + len) {
			return new String(b, offset, len, StandardCharsets.UTF_8);
		}
		return null;
	}

	public static String toString(final Long val) {
		return val != null ? Long.toString(val) : null;
	}

	public static int toInt(final Long val) {
		return val != null ? val.intValue() : 0;
	}

	public static int toIntFromLittle(final byte[] b) {
		return toIntFromLittle(b, 0, b.length);
	}

	public static int toIntFromLittle(final byte[] b, final int offset, final int length) {
		int ret = 0;
		for (int i = 0; i < length; i++) {
			ret += (b[offset + i] & 0xFF) << (i * 8);
		}
		return ret;
	}

	public static int toShort(final byte[] bytes) {
		return toShort(bytes, 0, SIZEOF_SHORT);
	}

	public static int toShort(final byte[] bytes, final int offset) {
		return toShort(bytes, offset, SIZEOF_SHORT);
	}

	public static int toShort(final byte[] bytes, final int offset, final int length) {
		if (length != SIZEOF_SHORT || offset + length > bytes.length) {
			throw new RuntimeException();
		}
		int n = 0;
		n ^= bytes[offset] & 0xFF;
		n <<= 8;
		n ^= bytes[offset + 1] & 0xFF;
		return n;
	}

	public static byte[] toBytes(final long n, final int length) {
		final byte[] b = new byte[length];
		for (int i = 0; i < length; i++) {
			b[i] = (byte) (n >> ((length - i - 1) * 8) & 0xFF);
		}
		return b;
	}

	public static String toHexString(final long n, final int length) {
		return String.format("%0" + length + "x", n);
	}

	public static String toHexString(final byte[] b) {
		return toHexString(b, false);
	}

	public static String toHexString(final byte[] b, final boolean upper) {
		if (b == null) {
			return null;
		}

		final char[] hexArray = upper ? HEXARRAY : HEXARRAY_LOWER;
		final char[] hexChars = new char[b.length * 2];
		int v;
		for (int j = 0; j < b.length; j++) {
			v = b[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static byte[] hexToBytes(final String s) {
		if (s == null || s.isEmpty()) return null;

		final int size = (int) Math.ceil(s.length() / 2.0);
		final String hex = StringUtils.leftPad(s, size * 2, "0");
		final byte[] b = new byte[size];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
		}
		return b;
	}

	public static String decodeText(final byte[] b, final int offset) {
		if (b != null && b.length >= offset + 2) {
			return toString(b, offset + 2, toShort(b, offset));
		}
		return null;
	}

	public static String decodeText(final byte[] b) {
		return decodeText(b, 0);
	}

	public static String trim(final String s) {
		return s != null ? s.trim() : null;
	}

	public static long toLong(final byte[] bytes) {
		return toLong(bytes, 0, 4);
	}

	public static long toLong(final byte[] bytes, final int offset) {
		return toLong(bytes, offset, 4);
	}

	public static long toLong(final byte[] bytes, final int offset, final int length) {
		if (bytes == null || length != 4 || offset + length > bytes.length) {
			return 0;
		}
		long l = 0;
		for (int i = 0; i < offset + length; i++) {
			l <<= 8;
			l ^= bytes[i] & 0xFF;
		}
		return l;
	}

	public static long toLongDR(final byte[] bytes) {
		return toLongDR(bytes, 0, 8);
	}

	public static long toLongDR(final byte[] bytes, final int offset, final int length) {
		if (bytes == null || length != 8 || offset + length > bytes.length) {
			return 0;
		}
		long l = 0;
		for (int i = 0; i < offset + length; i++) {
			l <<= 8;
			l ^= bytes[i] & 0xFF;
		}
		return l;
	}

	public static int putFloat(final byte[] bytes, final int offset, final float f) {
		return putInt(bytes, offset, Float.floatToRawIntBits(f));
	}

	public static int putInt(final byte[] bytes, final int offset, int val) {
		if (bytes.length - offset < 4) {
			throw new IllegalArgumentException("Not enough room to put an int at offset " + offset + " in a " + bytes.length + " byte array");
		}

		for (int i = offset + 3; i > offset; --i) {
			bytes[i] = (byte) val;
			val >>>= 8;
		}
		bytes[offset] = (byte) val;
		return (offset + 4);
	}

	public static int getDayOfWeek0to6(final DateTime dt) {
		final int dayOfWeek = dt.getDayOfWeek();
		return dayOfWeek == DateTimeConstants.SUNDAY ? 0 : dayOfWeek;
	}

	public static int getDayOfWeek1to7(final DateTime dt) {
		final int dayOfWeek = dt.getDayOfWeek();
		return dayOfWeek == DateTimeConstants.SUNDAY ? 1 : dayOfWeek + 1;
	}

	public static boolean isVaildField(final Object value) {
		return value instanceof String || value instanceof Date || value instanceof Integer || value instanceof Float || value instanceof Double || value instanceof Short || value instanceof Long;
	}

	public static Object newInstance(final String className) throws Exception {
		return newInstance(Class.forName(className));
	}

	public static Object newInstance(final String className, final Object... initargs) throws Exception {
		return newInstance(Class.forName(className), initargs);
	}

	public static <T> T newInstance(final Class<T> clazz, final Object... initargs) throws Exception {
		final List<Class<?>> parameterTypes = new LinkedList<>();
		for (final Object obj : initargs) {
			if (obj != null) {
				parameterTypes.add(obj.getClass());
			} else {
				parameterTypes.add(Object.class);
			}
		}
		return newInstance(clazz, parameterTypes.toArray(new Class<?>[0]), initargs);
	}

	public static Object newInstance(final String className, final Class<?>[] parameterTypes, final Object... initargs) throws Exception {
		return newInstance(Class.forName(className), parameterTypes, initargs);
	}

	public static <T> T newInstance(final Class<T> clazz, final Class<?>[] parameterTypes, final Object... initargs) throws Exception {
		final Constructor<T> constructor = clazz.getDeclaredConstructor(parameterTypes);
		return constructor.newInstance(initargs);
	}

	public static String getDomain(final String email) {
		String domain = null;
		if (email.contains("@")) {
			final String[] split = email.split("@");
			if (split.length > 1) {
				domain = split[1];
			}
		}
		return domain;
	}

	public static String digest(final String algorithm, final String filePath) {
		if (filePath == null || !new File(filePath).exists()) return null;

		try (FileInputStream is = new FileInputStream(filePath)) {
			final MessageDigest digest = MessageDigest.getInstance(algorithm);
			final byte[] buf = new byte[4096];
			int n;
			while ((n = is.read(buf)) != -1) {
				digest.update(buf, 0, n);
			}
			return toHexString(digest.digest());
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	public static byte[] digest(final String algorithm, final byte[]... inputs) throws NoSuchAlgorithmException {
		final MessageDigest digest = MessageDigest.getInstance(algorithm);
		for (final byte[] input : inputs) {
			digest.update(input);
		}
		return digest.digest();
	}

	public static byte[] digest(final String algorithm, final String... inputs) throws NoSuchAlgorithmException {
		final MessageDigest digest = MessageDigest.getInstance(algorithm);
		for (final String input : inputs) {
			digest.update(input.getBytes());
		}
		return digest.digest();
	}

	public static byte[] digestSupress(final String algorithm, final byte[]... inputs) {
		try {
			return digest(algorithm, inputs);
		} catch (final NoSuchAlgorithmException e) {
			return null;
		}
	}

	public static byte[] digestSupress(final String algorithm, final String... inputs) {
		try {
			return digest(algorithm, inputs);
		} catch (final NoSuchAlgorithmException e) {
			return null;
		}
	}

	public static byte[] md5(final String... inputs) {
		return digestSupress("MD5", inputs);
	}

	public static byte[] sha1(final String... inputs) {
		return digestSupress("SHA1", inputs);
	}

	public static byte[] sha256(final String... inputs) {
		return digestSupress("SHA-256", inputs);
	}

	public static byte[] sha256(final byte[]... inputs) {
		return digestSupress("SHA-256", inputs);
	}

	public static String stripTags(final String body) {
		return Jsoup.parse(body).text();
	}

	public static String stripNonValidChar(final String str) {
		if (str == null) {
			return "";
		}

		final char[] buffer = str.toCharArray();
		return stripNonValidChar(buffer, 0, buffer.length);
	}

	public static String stripNonValidChar(final char[] buffer, final int offset, final int nRead) {
		final StringBuilder sb = new StringBuilder();
		if (buffer != null && buffer.length - offset >= nRead) {
			for (int i = offset; i < offset + nRead; i++) {
				final char c = buffer[i];
				if (c == 0x9 || c == 0xA || c == 0xD || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD) {
					sb.append(c);
				}
			}
		}
		return sb.toString();
	}

	public static String stripNonValidCharNewline(final String str) {
		if (str == null) {
			return "";
		}

		final char[] buffer = str.toCharArray();
		return stripNonValidCharNewline(buffer, 0, buffer.length);
	}

	public static String stripNonValidCharNewline(final char[] buffer, final int offset, final int nRead) {
		final StringBuilder sb = new StringBuilder();
		if (buffer != null && buffer.length - offset >= nRead) {
			for (int i = offset; i < offset + nRead; i++) {
				final char c = buffer[i];
				if (c == 0x9 || c == 0xD || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD) {
					sb.append(c);
				}
			}
		}
		return sb.toString();
	}

	public static long diffTime(final long millis) {
		return System.currentTimeMillis() - millis;
	}

	public static <T> Collection<T> add(final Collection<T> collection, final Collection<T> item) {
		if (item != null) {
			collection.addAll(item);
		}
		return collection;
	}

	public static <T> Collection<T> add(final Collection<T> collection, final T[] item) {
		if (item != null) {
			collection.addAll(Arrays.asList(item));
		}
		return collection;
	}

	public static <T> Collection<T> add(final Collection<T> collection, final T item) {
		if (item != null) {
			collection.add(item);
		}
		return collection;
	}

	public static byte[] add(final byte[] a, final byte[] b) {
		return add(a, b, EMPTY_BYTE_ARRAY);
	}

	public static byte[] add(final byte[] a, final byte[] b, final byte[] c) {
		final byte[] result = new byte[a.length + b.length + c.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		System.arraycopy(c, 0, result, a.length + b.length, c.length);
		return result;
	}

	public static boolean isProcessExited(final Process proc) {
		try {
			proc.exitValue();
			return true;
		} catch (final IllegalThreadStateException e) {
			return false;
		}
	}

	public static void copy(final InputStream in, final OutputStream... outs) throws IOException {
		copy(in, null, outs);
	}

	public static byte[] copy(final InputStream in, final boolean compress, final String digestAlgorithm, OutputStream out, final OutputStream tmpRaw) throws IOException {
		if (compress) {
			out = new GZIPOutputStream(out);
		}
		try {
			return copy(in, digestAlgorithm, out, tmpRaw);
		} finally {
			if (compress) {
				((GZIPOutputStream) out).finish();
			}
		}
	}

	public static byte[] copy(final InputStream in, final String digestAlgorithm, final OutputStream... outs) throws IOException {
		try {
			MessageDigest digest = null;
			if (digestAlgorithm != null) {
				digest = MessageDigest.getInstance(digestAlgorithm);
			}

			final byte[] buf = new byte[4096];
			int nread;
			while ((nread = in.read(buf)) != -1) {
				if (digest != null) {
					digest.update(buf, 0, nread);
				}

				for (final OutputStream out : outs) {
					if (out != null) {
						out.write(buf, 0, nread);
					}
				}
			}

			byte[] hash = null;
			if (digest != null) {
				hash = digest.digest();
			}
			return hash;
		} catch (final Exception e) {
			throw new IOException(e);
		} finally {
			for (final OutputStream out : outs) {
				if (out != null) {
					out.flush();
				}
			}
		}
	}

	public static void copy(final InputStream in, final boolean compress, final CIPHER cipher, final byte[] key, final long srcLen, final OutputStream out) throws Exception {
		copy(in, compress, null, cipher, key, srcLen, out);
	}

	public static byte[] copy(final InputStream in, final boolean compress, final String digestAlgorithm, final CIPHER cipher, final byte[] key, final long srcLen, final OutputStream out) throws Exception {
		return copy(in, compress, digestAlgorithm, cipher, key, srcLen, out, null);
	}

	public static byte[] copy(InputStream in, final boolean compress, final String digestAlgorithm, final CIPHER cipher, final byte[] key, final long srcLen, OutputStream out, final OutputStream tmpRaw) throws Exception {
		in = new CryptoInputStream(new Crypto(key, cipher), in);
		final long decLength = ((CryptoInputStream) in).getLength();
		try {
			out = new CryptoOutputStream(new Crypto(key, cipher), out, decLength < 0 ? srcLen : decLength);
			return copy(in, compress, digestAlgorithm, out, tmpRaw);
		} finally {
			out.close();
		}
	}

	public static String strip(final String str) {
		return strip(str, '"');
	}

	public static String strip(final String str, final char stripChar) {
		if (str == null || str.isEmpty()) {
			return str;
		}

		final int begin = str.charAt(0) == stripChar ? 1 : 0;
		final int offset = str.length() - 1;
		final int end = str.charAt(offset) == stripChar ? offset : str.length();

		if (begin > 0 || end < str.length()) {
			return str.substring(begin, end);
		} else {
			return str;
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T coalesce(final Object object, final T defaultValue) {
		return object != null ? ((T) object) : defaultValue;
	}

	public static String getParentDir(final String path) {
		final int lastSlash = path.lastIndexOf('/');
		if (lastSlash < 0 || lastSlash == 0 && path.length() == 1) {
			return null;
		}
		return path.substring(0, lastSlash == 0 ? 1 : lastSlash);
	}

	public static String toBase32(final byte[] b) {
		return new Base32().encodeAsString(b);
	}

	public static String toBase64(final byte[] b) {
		return Base64.encodeBase64String(b);
	}

	public static String stringifyException(final Throwable e) {
		final StringWriter stm = new StringWriter();
		final PrintWriter wrt = new PrintWriter(stm);
		e.printStackTrace(wrt);
		wrt.close();
		return stm.toString();
	}

	public static String getPid() {
		return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	}

	public static boolean parseBoolean(final String value) {
		return StringUtils.isNotEmpty(value) && ("Y".equalsIgnoreCase(value) || "T".equalsIgnoreCase(value) || "1".equals(value) || "YES".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value));
	}

	public static String trimUriDecoder(final String uri) {
		int trimLen = 0;
		if (uri.length() > 1 && uri.charAt(uri.length() - 1) == '%') {
			trimLen = 1;
		} else if (uri.length() > 2 && uri.charAt(uri.length() - 2) == '%') {
			trimLen = 2;
		}
		return trimLen == 0 ? uri : uri.substring(0, uri.length() - trimLen);
	}

	// guava 19.0 method 호환
	public static final class LimitedInputStream extends FilterInputStream {
		private long left;
		private long mark = -1L;

		public LimitedInputStream(InputStream in, long limit) {
			super(in);
			Preconditions.checkNotNull(in);
			Preconditions.checkArgument(limit >= 0L, "limit must be non-negative");
			this.left = limit;
		}

		@Override
		public int available() throws IOException {
			return (int) Math.min(this.in.available(), this.left);
		}

		@Override
		public synchronized void mark(int readLimit) {
			this.in.mark(readLimit);
			this.mark = this.left;
		}

		@Override
		public int read() throws IOException {
			if (this.left == 0L) {
				return -1;
			}

			int result = this.in.read();
			if (result != -1) {
				this.left -= 1L;
			}
			return result;
		}

		@Override
		public int read(@NotNull byte[] b, int off, int len) throws IOException {
			if (this.left == 0L) {
				return -1;
			}

			len = (int) Math.min(len, this.left);
			int result = this.in.read(b, off, len);
			if (result != -1) {
				this.left -= result;
			}
			return result;
		}

		@Override
		public synchronized void reset() throws IOException {
			if (!(this.in.markSupported())) {
				throw new IOException("Mark not supported");
			}
			if (this.mark == -1L) {
				throw new IOException("Mark not set");
			}

			this.in.reset();
			this.left = this.mark;
		}

		@Override
		public long skip(long n) throws IOException {
			n = Math.min(n, this.left);
			long skipped = this.in.skip(n);
			this.left -= skipped;
			return skipped;
		}
	}

	public static String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			log.error("", e);
		}
		return null;
	}

	public static boolean isWindow() {
		return System.getProperty("os.name").toLowerCase().startsWith("windows");
	}

	public static boolean isOrEquals(Object source, String[] target) {
		if (source == null) return false;
		boolean result = false;
		for (String s : target) {
			result = source.equals(nvl(s));
			if (result) break;
		}
		return result;
	}

	public static boolean isOrEquals(Object source, Object... target) {
		if (source == null) return false;
		boolean result = false;
		for (Object o : target) {
			result = source.equals(nvl(o));
			if (result) break;
		}
		return result;
	}

	public static boolean isEquals(Object source, Object target) {
		if (source == null) return false;
		else if (target == null) return true;
		else return source.equals(target);
	}

	public static boolean isNotEquals(Object source, Object target) {
		if (source == null) return true;
		else if (target == null) return false;
		else return !source.equals(target);
	}

	/**
	 * Java String isEmpty This Java String isEmpty shows how to check whether the
	 * given string is empty or not using isEmpty method of Java String class.
	 */
	public static boolean isEmpty(Object target) {
		return nvl(target).isEmpty();
	}

	/**
	 * Java String isEmpty This Java String isEmpty shows how to check whether the
	 * given string is empty or not using isEmpty method of Java String class.
	 */
	public static boolean isNotEmpty(Object target) {
		return !isEmpty(target);
	}

	/**
	 * Null to Empty String
	 */
	public static String nvl(Object target) {
		return nvl(target, EMPTY);
	}

	/**
	 * Null to Empty String
	 */
	public static String nvl(Object target, String defaultStr) {
		if (target != null && !String.valueOf(target).equalsIgnoreCase("null")) return String.valueOf(target);
		return defaultStr;
	}

	/**
	 * Null to Empty String
	 */
	public static long nvn(Object target) {
		return nvn(target, 0L);
	}

	/**
	 * Null to Empty String
	 */
	public static long nvn(Object target, long defaultNum) {
		if (target != null) {
			if (!String.valueOf(target).equalsIgnoreCase("null") && !String.valueOf(target).isEmpty())
				return Long.parseLong(String.valueOf(target));
		}
		return defaultNum;
	}

	/**
	 * Null to Empty String
	 */
	public static int nvz(Object target) {
		return nvz(target, 0);
	}

	/**
	 * Null to Empty String
	 */
	public static int nvz(Object target, int defaultNum) {
		if (target != null) {
			if (!String.valueOf(target).equalsIgnoreCase("null") && !String.valueOf(target).isEmpty()) {
				try {
					return Integer.parseInt(String.valueOf(target));
				} catch (Exception e) {
					return defaultNum;
				}
			}
		}
		return defaultNum;
	}

	public static boolean isArrEmpty(JSONArray a) {
		return a == null || a.isEmpty();
	}

	public static boolean isObjEmpty(JSONObject o) {
		return o == null || o.isEmpty();
	}

	public static String makeMsgId(final DateTime ctime, final String filePath) {
		return String.format("%s.%s", ctime.toString(Constants.YYYYMMDDHHMMSS), CommonUtil.toBase32(CommonUtil.sha1(filePath)));
	}

	public static IP toIP(final String str) {
		if (str != null && !str.isEmpty() && !"unknown".equals(str)) {
			try {
				return IP.create(StringUtils.split(str, ":, ")[0]);
			} catch (Exception e) {
				log.warn("str: {}", str, e);
			}
		}
		return null;
	}

	public static List<EMail> toEmails(final String[] addrs) {
		List<EMail> result = new ArrayList<>();
		for (String addr : addrs) {
			result.add(toEmail(addr));
		}
		return result;
	}

	public static EMail toEmail(final String str) {
		if (StringUtils.isEmpty(str)) return null;
		try {
			return EMail.parse(str);
		} catch (Exception e) {
			log.warn("str: {}", str, e);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static List<EMail> toEmail(final Object obj) {
		if (obj == null) return Collections.emptyList();
		List<EMail> emails = new ArrayList<>();
		try {
			if (obj instanceof Collection) {
				emails = EMail.parse((Collection<String>) obj);
			} else if (obj instanceof String[]) {
				emails = EMail.parse((String[]) obj);
			} else if (obj instanceof byte[]) {
				emails = EMail.parse(obj);
			} else {
				final EMail email = EMail.parse((String) obj);
				if (email != null) emails.add(email);
			}
		} catch (Exception e) {
			log.warn("obj: {}", obj, e);
		}
		return emails;
	}

	public static String[] toArray(final String str, final String prefix) {
		return toArray(str, prefix, true);
	}

	public static String[] toArray(final String str, final String prefix, final boolean exceptEmpty) {
		if (str == null) return new String[0];

		// 정규식 특수 문자 처리를 위해 prefix 이스케이프 처리
		String escapedPrefix = java.util.regex.Pattern.quote(prefix);
		String[] tmp = str.split(escapedPrefix);
		List<String> as = new ArrayList<>();
		for (String x : tmp) {
			if (isNotEmpty(x) || !exceptEmpty) as.add(x);
		}
		return as.toArray(new String[0]);
	}

	@SuppressWarnings("unchecked")
	public static List<String> toList(final String key, Map<String, Object> data) {
		return (List<String>) data.get(key);
	}

	@SuppressWarnings("unchecked")
	public static List<String> toList4Default(final String key, Map<String, Object> data) {
		if (data.get(key) == null) return new ArrayList<>();
		return (List<String>) data.get(key);
	}

	public static List<String> readLines(final Reader reader) throws IOException {
		final List<String> list = new ArrayList<>();
		try (final BufferedReader bufReader = toBufferedReader(reader)) {
			String line;
			while ((line = bufReader.readLine()) != null) {
				list.add(line);
			}
		}
		return list;
	}

	public static List<String> splitDateRange(String startDay, String endDay) {
		// 시작일과 종료일을 LocalDate 객체로 변환
		LocalDate startDate = LocalDate.parse(startDay, DateTimeFormatter.BASIC_ISO_DATE);
		LocalDate endDate = LocalDate.parse(endDay, DateTimeFormatter.BASIC_ISO_DATE);

		List<String> dateList = new ArrayList<>();

		// 시작일부터 종료일까지 날짜를 하나씩 추가
		while (!startDate.isAfter(endDate)) {
			dateList.add(startDate.format(DateTimeFormatter.BASIC_ISO_DATE));
			startDate = startDate.plusDays(1); // 다음 날짜로 이동
		}

		return dateList;
	}

	public static BufferedReader toBufferedReader(final Reader reader) {
		return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
	}


	public static String makeMD5Hex(String... params) {
		return toHexString(md5(StringUtils.join(params)), true);
	}

	public static boolean isNumeric(String s) {
		try {
			Double.parseDouble(s);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static void executorAwaitTermination(ExecutorService es, int seconds) {
		es.shutdown();
		try {
			if (!es.awaitTermination(seconds, TimeUnit.SECONDS)) {
				es.shutdownNow();
				es.awaitTermination(10L, TimeUnit.SECONDS);
			}
		} catch (InterruptedException ignored) {
		}
	}

	public static String formatNumber(long number) {
		NumberFormat formatter = NumberFormat.getInstance(Locale.US);
		return formatter.format(number);
	}

	/**
	 * 용량 계산
	 *
	 * @param size 바이트
	 * @return 용량 표기
	 */
	public static String convertFileSize(long size) {
		if (size <= 0) return "0 KB";
		final String[] units = new String[]{" Byte", " KB", " MB", " GB", " TB", " PB", " EB", " ZB", " YB"};
		int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
		return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + units[digitGroups];
	}

	public static String sanitizeFileName(String fileName, char[] errorChars) {
		if (fileName == null) return null;
		for (char c : errorChars) {
			fileName = fileName.replace(String.valueOf(c), "");
		}
		return fileName;
	}

	public static String processFirstLine(final List<String> command, final int limit) {
		if (command == null || command.isEmpty()) return null;

		LimitedBufferedReader reader = null;
		Process proc = null;
		try {
			proc = new ProcessBuilder(command).start();
			reader = new LimitedBufferedReader(new InputStreamReader(new LimitedInputStream(proc.getInputStream(), limit)));
			return reader.readLine();
		} catch (Exception e) {
			log.error("", e);
		} finally {
			IOUtils.closeQuietly(reader);
			if (proc != null) proc.destroy();
		}
		return null;
	}

	public static String process(final List<String> command, long limitMillis) {
		StringBuilder sb = new StringBuilder();
		if (command == null || command.isEmpty()) return sb.toString();
		try {
			long startTime = System.currentTimeMillis();
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String str;
				long deadline = System.currentTimeMillis() + limitMillis;
				while ((str = br.readLine()) != null && System.currentTimeMillis() < deadline) {
					sb.append(str).append("\n");
				}
			}
			boolean finished = process.waitFor(Math.max(0, limitMillis - (System.currentTimeMillis() - startTime)), TimeUnit.MILLISECONDS);
			if (!finished) process.destroyForcibly();

			log.debug("[PROCESS] {}", DateUtils.duration(startTime));
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
		return sb.toString();
	}

	public static void mkdir(File file) {
		try {
			FileUtils.forceMkdir(file);
		} catch (IOException e) {
			log.error("", e);
		}
	}

	public static String readFirstLine(String filePath) {
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			return reader.readLine();
		} catch (IOException e) {
			log.warn("[TXT_READ] {} | {}", filePath, e.getMessage());
			log.error("", e);
		}
		return null;
	}


	public static <T> List<T> nvl(List<T> v) {
		return v != null ? v : Collections.emptyList();
	}

	public static <T> T get(List<T> v, int i) {
		return (v != null && i < v.size()) ? v.get(i) : null;
	}

	public static String getSummaryText(final String text) {
		if (text == null || text.isEmpty()) return null;
		return text.substring(0, Math.min(text.length(), 20)).replaceAll("\n", " ") + "...";
	}

	/**
	 * 연속 "비공백" 토큰의 최대 길이를 제한하고, 초과 시 공백을 삽입한다.
	 */
	public static String limitTokenLengthWithSpace(String input, int maxTokenLen) {
		if (input == null || input.isEmpty() || maxTokenLen <= 0) return input;
		try {
			StringBuilder sb = new StringBuilder(input.length() + input.length() / maxTokenLen);
			int tokenLen = 0;
			for (int i = 0; i < input.length(); ) {
				int cp = input.codePointAt(i);
				int charCount = Character.charCount(cp);

				if (Character.isWhitespace(cp)) {
					sb.appendCodePoint(cp);
					tokenLen = 0;
				} else {
					if (tokenLen >= maxTokenLen) {
						if (sb.isEmpty() || sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
						tokenLen = 0;
					}
					sb.appendCodePoint(cp);
					tokenLen++;
				}
				i += charCount;
			}
			return sb.toString();
		} catch (Exception e) {
			log.warn("[TXT_LIMIT] {}", e.getMessage());
		}
		return input;
	}

	public static String limitLength(String input, int maxLength) {
		if (input == null) return null;
		if (input.length() <= maxLength) {
			return input;
		}
		return input.substring(0, maxLength);
	}

	public static String unescapeJava(final String text) {
		if (text == null || text.isEmpty()) return text;
		return text.replace("\\r\\n", "\r\n").replace("\\\\r\\\\n", "\r\n").replace("\\\\n", "\n").replace("\\n", "\n");
	}
}
