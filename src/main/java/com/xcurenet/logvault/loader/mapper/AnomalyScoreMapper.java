package com.xcurenet.logvault.loader.mapper;

import com.xcurenet.logvault.loader.type.AnomalyScoreVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AnomalyScoreMapper {

	@Select("""
			SELECT
				    MAPR_TABLE AS mapperTable,
				    TARGET_ID AS targetId,
					ANOMALY_LEVEL_CD AS anomalyLevelCd
			FROM	UI_ANOMALY_SCORE
			""")
	@ResultType(AnomalyScoreVO.class)
	List<AnomalyScoreVO> getAnomalyScoreList();
}
