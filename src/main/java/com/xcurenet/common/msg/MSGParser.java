package com.xcurenet.common.msg;

import com.xcurenet.common.types.*;
import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.DateUtils;
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
		try {
			final String input = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
			MSGData data = convertData(parseInfoText(input));
			data.setInfoFilePath(filePath);
			data.setInfoText(input);
			data.setFileNameInfo(FileNameInfo.getInfo(filePath));
			data.setMsgid(CommonUtil.makeMsgId(data.getCtime(), CommonUtil.makeFilepath(data.getInfoFilePath())));
			return data;
		} catch (Exception e) {
			throw new ProcessDataException(e);
		}
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
		String strVal = CommonUtil.nvl(value);
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
