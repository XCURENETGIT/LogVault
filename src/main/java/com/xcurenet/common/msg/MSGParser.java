package com.xcurenet.common.msg;

import com.xcurenet.common.error.ErrorCode;
import com.xcurenet.common.types.AttachExtension;
import com.xcurenet.common.types.EMail;
import com.xcurenet.common.types.FileNameInfo;
import com.xcurenet.common.types.IP;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.common.utils.ExFactory;
import com.xcurenet.logvault.exception.ProcessDataException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class MSGParser {
	private static final Pattern ARRAY_KEY = Pattern.compile("(\\w+)\\[(\\d+)]");
	public static final char[] ERROR_CHAR = new char[]{'{', '}', ':', '"'};

	public static MSGData parse(final String filePath) throws ProcessDataException {
		File file = new File(filePath);
		String input;
		try {
			input = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_READ_FAIL, Map.of("context", filePath), e);
		}

		if (input == null)
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_TEXT_NULL, Map.of("context", filePath));

		MSGData data;
		try {
			data = convertData(parseInfoText(input));
		} catch (Exception e) {
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_WORK_FAIL, Map.of("context", input), e);
		}

		checkField(data, input);

		try {
			data.setFileNameInfo(FileNameInfo.getInfo(filePath));
		} catch (Exception e) {
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_FILENAME_FAIL, Map.of("context", filePath), e);
		}

		try {
			data.setInfoFilePath(filePath);
			data.setInfoText(input);
			data.setMsgid(Common.makeMsgId(data.getCtime(), Common.makeFilepath(data.getInfoFilePath())));
			return data;
		} catch (Exception e) {
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_INVALID, Map.of("context", filePath), e);
		}
	}

	private static void checkField(final MSGData data, final String input) {
		if (data.getCtime() == null)
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_CTIME_NULL, Map.of("context", input));
		if (data.getSourceIp() == null)
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_SIP_NULL, Map.of("context", input));
		if (data.getSourcePort() == 0)
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_SPORT_NULL, Map.of("context", input));
		if (data.getDestinationIp() == null)
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_DIP_NULL, Map.of("context", input));
		if (Common.isEmpty(data.getHost()))
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_HOST_NULL, Map.of("context", input));
		if (Common.isEmpty(data.getUrl()))
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_URL_NULL, Map.of("context", input));
		if (Common.isNotEmpty(data.getQuery()) && data.getQuery().length() > 4000) {
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_QUERY_TOO_LONG, Map.of("context", Common.nvl(data.getQuery().length())));
		}
		if (Common.isEmpty(data.getHeader()))
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_HEADER_NULL, Map.of("context", input));
		if (Common.isEmpty(data.getSvc()))
			throw ExFactory.ex(ProcessDataException::new, ErrorCode.PARSER_STYPE_NULL, Map.of("context", input));
	}

	private static Map<String, Object> parseInfoText(String input) {
		Map<String, Object> map = new HashMap<>();
		if (input == null) return map;

		try (BufferedReader reader = new BufferedReader(new StringReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("[") && line.endsWith("]")) {
					map.put("SVCKIND", line.substring(1, line.length() - 1));
					continue;
				}

				String[] fields = line.split(":", 2);
				if (fields.length < 2) continue;

				String key = fields[0].trim();
				String value = fields[1].trim();

				if (key.endsWith("]") && key.charAt(0) != '[') {
					Matcher matcher = ARRAY_KEY.matcher(key);
					if (matcher.find()) {
						key = matcher.group(1);
						int index = Integer.parseInt(matcher.group(2));
						@SuppressWarnings("unchecked")
						List<String> list = (List<String>) map.computeIfAbsent(key, k -> new ArrayList<>());
						while (list.size() <= index) list.add(null);
						list.set(index, value);
						continue;
					}
				}
				map.put(key, value);
			}
		} catch (IOException e) {
			log.error("Error parsing info text", e);
		}
		return map;
	}

	private static MSGData convertData(Map<String, Object> keyValueMap) throws Exception {
		MSGData dataStruct = MSGData.class.getDeclaredConstructor().newInstance();
		for (Field field : MSGData.class.getDeclaredFields()) {
			field.setAccessible(true);
			if (!field.isAnnotationPresent(FieldKey.class)) continue;

			for (String key : field.getAnnotation(FieldKey.class).value()) {
				if (keyValueMap.containsKey(key)) {
					field.set(dataStruct, parseValue(field.getType(), keyValueMap.get(key), field));
					break;
				}
			}
		}
		return dataStruct;
	}

	private static Object parseValue(Class<?> type, Object value, Field field) throws IOException {
		String strVal = Common.nvl(value);
		if ("userIp".equals(field.getName()) && !"unknown".equals(strVal)) {
			return IP.create(StringUtils.split(strVal, ":, ")[0]);
		}

		if (type == String.class) return value;
		if (type == int.class || type == Integer.class) return Integer.parseInt(strVal);
		if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(strVal);
		if (type == IP.class) return new IP(strVal);
		if (type == DateTime.class) return DateUtils.parseDateTime(strVal);
		if (type == EMail.class) return EMail.parse(strVal);
		if (type == AttachExtension.class) return new AttachExtension(strVal);
		if (List.class.isAssignableFrom(type)) {
			Class<?> elementType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
			List<Object> resultList = new ArrayList<>();
			for (Object item : (List<?>) value) {
				resultList.add(parseValue(elementType, item, field));
			}
			return resultList;
		}
		return null;
	}
}
