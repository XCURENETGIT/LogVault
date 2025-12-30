package com.xcurenet.common.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class GenericJsonListTypeHandler extends BaseTypeHandler<List<?>> {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, List<?> parameter, JdbcType jdbcType) throws SQLException {
		try {
			ps.setString(i, objectMapper.writeValueAsString(parameter));
		} catch (JsonProcessingException e) {
			throw new SQLException("JSON serialize error", e);
		}
	}

	@Override
	public List<?> getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return parse(rs.getString(columnName));
	}

	@Override
	public List<?> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return parse(rs.getString(columnIndex));
	}

	@Override
	public List<?> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return parse(cs.getString(columnIndex));
	}

	private List<?> parse(String json) throws SQLException {
		if (json == null || json.isBlank()) {
			return List.of();
		}

		TypeReference<?> typeRef = JsonTypeContext.get();
		if (typeRef == null) {
			throw new SQLException("JsonTypeContext TypeReference not set");
		}

		try {
			return (List<?>) objectMapper.readValue(json, typeRef);
		} catch (Exception e) {
			throw new SQLException("JSON parse error", e);
		} finally {
			JsonTypeContext.clear();
		}
	}
}
