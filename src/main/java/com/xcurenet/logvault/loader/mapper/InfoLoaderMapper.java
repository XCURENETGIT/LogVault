package com.xcurenet.logvault.loader.mapper;

import com.xcurenet.common.mybatis.GenericJsonListTypeHandler;
import com.xcurenet.logvault.loader.type.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface InfoLoaderMapper {

	@Select("""
			SELECT IFNULL(MAX(RULE_VERSION),0) AS RULE_VERSION
			FROM UI_USERS_RULE;
			""")
	@ResultType(Long.class)
	long getLastUserInfo();

	@Select("""
			SELECT	USER_CD AS userId,
					USER_NM AS name,
					EMAIL AS email,
					GROUP_CD AS deptCd,
					GROUP_NM AS deptNm,
					JIKGUB_CD AS jikgubCd,
					JIKGUB_NM AS jikgubNm,
					IP AS ip,
					IS_CEO AS ceo
			FROM	UI_USERS_RULE
			WHERE	RULE_VERSION = #{ruleVersion}
			ORDER	BY USER_CD
			""")
	@ResultType(UserInfo.class)
	List<UserInfo> getUserInfo(@Param("ruleVersion") long ruleVersion);

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
			FROM	UI_SERVICE
			WHERE	LOGGING_YN = 'Y'
			AND		USE_YN = 'Y'
			""")
	@ResultType(ServiceVO.class)
	List<ServiceVO> getService();

	@Select("""
			SELECT	RULE_CONTENT AS ruleContent
			FROM	UI_RULE_HISTORY
			WHERE	RULE_TABLE_NAME = #{ruleTableName}
			AND		RULE_VERSION = #{ruleVersion}
			LIMIT	1
			""")
	@Result(column = "ruleContent", property = "ruleContent", typeHandler = GenericJsonListTypeHandler.class)
	@ResultType(RuleContentWrapper.class)
	RuleContentWrapper getRuleHistory(@Param("ruleTableName") String ruleTableName, @Param("ruleVersion") long ruleVersion);


	@Select("""
			SELECT	IFNULL(MAX(RULE_VERSION),0) AS RULE_VERSION
			FROM	UI_RULE_HISTORY
			WHERE	RULE_TABLE_NAME = #{ruleTableName}
			""")
	@ResultType(Long.class)
	long getLastVersion(@Param("ruleTableName") String ruleTableName);
}
