package com.xcurenet.common.utils;

import com.xcurenet.common.Constants;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class DateUtils {
	private DateUtils() {
		throw new IllegalStateException("Utility class");
	}

	public static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormat.forPattern("yyyyMMddHHmmss");
	public static final DateTimeFormatter DATETIME_CTIME = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss");

	private static final String YYYYMMDD = "yyyyMMdd";
	public static final String YYYYMMDDHHMMSSSSS = "yyyyMMddHHmmssSSS";
	private static final int YYYYMMDD_SIZE = 8;
	private static final int YYYYMMDDHH_SIZE = 10;
	private static final int YYYYMMDDHHMM_SIZE = 12;
	private static final int YYYYMMDDHHMMSS_SIZE = 14;

	/**
	 * 년월일에 해당하는 문자열이 날짜 형식인지 체크
	 *
	 * @param date : YYYYMMDD
	 */
	public static boolean validDate4yyyymmdd(final String date) {
		return validDate(date, YYYYMMDD);
	}

	public static boolean validDate(final String date, final String format) {
		return validDate(date, DateTimeFormat.forPattern(format));
	}

	public static boolean validDate(final String date, final DateTimeFormatter format) {
		try {
			format.parseDateTime(date);
		} catch (Exception e) {
			log.warn("Input : {}, Error : {}", date, e.getMessage());
			return false;
		}
		return true;
	}

	public static String convertDateTime(final String dt, final DateTimeFormatter src, final DateTimeFormatter dst) {
		return src.parseDateTime(dt).toString(dst);
	}

	public static DateTime parseDateTimeYYYYMMDD(final String dt) {
		if (dt == null || dt.isEmpty()) return null;
		return DateTimeFormat.forPattern(YYYYMMDD).parseDateTime(dt);
	}

	public static DateTime parseDateTime(final String dt) {
		if (dt == null || dt.isEmpty()) {
			return null;
		}
		final String stripDt = StringUtils.replaceChars(dt, "- :./", "");
		final int length = Math.min(stripDt.length(), YYYYMMDDHHMMSSSSS.length());
		final DateTimeFormatter fmt = DateTimeFormat.forPattern(YYYYMMDDHHMMSSSSS.substring(0, length));
		return fmt.parseDateTime(stripDt.substring(0, length));
	}

	public static DateTime getDateTime(String day) {
		DateTimeFormatter formatter = null;
		switch (day.length()) {
			case YYYYMMDD_SIZE:
				formatter = DateTimeFormat.forPattern("yyyyMMdd").withZoneUTC();
				break;
			case YYYYMMDDHH_SIZE:
				formatter = DateTimeFormat.forPattern("yyyyMMddHH").withZoneUTC();
				break;
			case YYYYMMDDHHMM_SIZE:
				formatter = DateTimeFormat.forPattern("yyyyMMddHHmm").withZoneUTC();
				break;
			case YYYYMMDDHHMMSS_SIZE:
				formatter = DateTimeFormat.forPattern("yyyyMMddHHmmss").withZoneUTC();
				break;
		}
		assert formatter != null;
		return formatter.parseDateTime(day);
	}

	public static Date getCurrentTime() {
		DateTime currentTime = DateTime.now();
		DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
		return currentTime.toDate();
	}

	public static String duration(long startTimeMillis) {
		long durationMillis = System.currentTimeMillis() - startTimeMillis;
		Duration d = Duration.ofMillis(durationMillis);
		long seconds = d.toSecondsPart();  // Java 9+
		int millis = d.toMillisPart();     // Java 9+
		return String.format("%d.%03ds", seconds, millis);
	}

	public static String durationFormatter(long durationMillis) {
		return DurationFormatUtils.formatDuration(durationMillis, "HH:mm:ss.SSS");
	}


	public static DateTime getDate(final File file) {
		try {
			Pattern pattern = Pattern.compile("(20\\d{12})");
			Matcher matcher = pattern.matcher(file.getName());
			if (matcher.find()) {
				LocalDateTime odt = LocalDateTime.parse(matcher.group(1), Constants.YYYYMMDDHHMMSS);
				return odt.toDateTime(DateTimeZone.UTC);
			}
		} catch (Exception e) {
			log.error("error", e);
		}
		return getFileCreationTime(file);
	}

	private static DateTime getFileCreationTime(final File file) {
		try {
			BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
			FileTime time = attr.creationTime();
			return new LocalDateTime(time.toMillis()).toDateTime(DateTimeZone.UTC);
		} catch (Exception e) {
			return new LocalDateTime().toDateTime(DateTimeZone.UTC);
		}
	}

	/**
	 * long timestamp(System.currentTimeMillis()) → "yyyyMMddHHmmss" 문자열 변환
	 */
	public static String formatToYYYYMMDDHHMMSS(long millis) {
	    return new DateTime(millis).toString(YYYYMMDDHHMMSS);
	}
}
