package com.xcurenet.logvault.loader.mapper;

import com.xcurenet.common.mybatis.GenericJsonListTypeHandler;
import com.xcurenet.logvault.loader.type.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface InfoLoaderMapper {

	@Select("""
			SELECT	A.USER_CD AS userId,
					A.USER_NM AS name,
					A.GROUP_CD AS deptCd,
					B.GROUP_NM AS deptNm,
					A.JIKGUB_CD AS jikgubCd,
					C.JIKGUB_NM AS jikgubNm,
					A.IP AS ip,
					A.IS_CEO AS ceo
			FROM	UI_USERS A
					LEFT JOIN UI_GROUPS B ON (A.GROUP_CD  = B.GROUP_CD)
					LEFT JOIN UI_JIKGUB C  ON (A.JIKGUB_CD = C.JIKGUB_CD)
			ORDER	BY A.USER_CD
			""")
	@ResultType(UserInfo.class)
	List<UserInfo> getUserInfo();

	@Select("""
			SELECT  (SELECT  VAL FROM UI_CONF WHERE CONF_ID = 'work.day') AS wDay,
					(SELECT  VAL FROM UI_CONF WHERE CONF_ID = 'work.hour') AS wHour
			""")
	@ResultType(WorkDayInfo.class)
	WorkDayInfo getWorkDay();

	@Select("""
			SELECT	PATTERN_CD AS patternCd,
					PATTERN_NM AS patternNm,
					PATTERN_TYPE AS patternType,
					REGEX AS regex,
					MIN_CNT AS minCount,
					ALARM_YN AS alarmYn,
					SYSLOG_YN AS syslogYn
			FROM	UI_PATTERN
			WHERE	USE_YN = 'Y'
			""")
	@ResultType(PatternInfo.class)
	List<PatternInfo> getPatternInfo();

	@Select("""
			SELECT	SERVICE_CD AS serviceCd, SERVICE_NAME AS serviceName
			FROM	AEGISAI.UI_SERVICE
			WHERE	LOGGING_YN = 'Y'
			AND		USE_YN = 'Y'
			""")
	@ResultType(ServiceVO.class)
	List<ServiceVO> getService();

	@Select("""
			SELECT	RULE_CONTENT AS ruleContent
			FROM	UI_RULE_HISTORY
			WHERE	RULE_TABLE_NAME = #{ruleTableName}
			ORDER	BY RULE_VERSION DESC
			LIMIT	1
			""")
	@Result(column = "ruleContent", property = "ruleContent", typeHandler = GenericJsonListTypeHandler.class)
	@ResultType(RuleContentWrapper.class)
	RuleContentWrapper getRuleHistory(@Param("ruleTableName") String ruleTableName);
}
