package com.xcurenet.logvault.module.task.service;

import com.xcurenet.logvault.job.delete.DeleteMessage;
import com.xcurenet.logvault.module.alert.AlertMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TaskMessageRepository {

	@Select("""
			SELECT  MSGID AS msgId, TASK_TYPE AS taskType, STATUS AS status, DATA AS data, ERROR_MESSAGE AS errorMessage, RUN_AT AS runAt, CREATE_DT AS createDt
			FROM	AI_PROCESSING_QUEUE
			WHERE	TASK_TYPE = #{taskType}
			AND		STATUS = 'PENDING'
			ORDER 	BY MSGID
			LIMIT	#{limit}
			FOR 	UPDATE SKIP LOCKED
			""")
	@ResultType(TaskMessage.class)
	List<TaskMessage> claimBatchByType(@Param("taskType") String taskType, @Param("limit") int limit);

	@Update("""
			UPDATE  AI_PROCESSING_QUEUE
			SET		STATUS = 'PENDING'
			WHERE	STATUS = 'RUNNING'
			""")
	void updateStatusPending();

	@Update("""
			UPDATE	AI_PROCESSING_QUEUE
			SET 	STATUS  = 'RUNNING',
					RUN_AT  = NOW()
			WHERE	MSGID   = #{msgId}
			AND     TASK_TYPE = #{taskType}
			""")
	void updateStatusRunning(@Param("msgId") String id, @Param("taskType") String taskType);

	@Update("""
			DELETE
			FROM 	AI_PROCESSING_QUEUE
			WHERE 	MSGID = #{msgId}
			AND     TASK_TYPE = #{taskType}
			""")
	void deleteById(@Param("msgId") String id, @Param("taskType") String taskType);

	@Update("""
			UPDATE  AI_PROCESSING_QUEUE
			SET		STATUS = 'FAILED',
					ERROR_MESSAGE = #{err}
			WHERE 	MSGID = #{msgId}
			AND     TASK_TYPE = #{taskType}
			""")
	void updateStatusFailed(@Param("msgId") String id, @Param("taskType") String taskType, @Param("err") String err);


	@Update("""
			UPDATE	AI_PROCESSING_QUEUE
			SET		STATUS='DONE',
					ERROR_MESSAGE=NULL
			WHERE	MSGID= #{msgId}
			AND     TASK_TYPE = #{taskType}
			""")
	void updateStatusDone(@Param("msgId") String id, @Param("taskType") String taskType);

	@Insert("""
			INSERT IGNORE INTO AI_PROCESSING_QUEUE (MSGID, TASK_TYPE, STATUS, `DATA`, ERROR_MESSAGE, RUN_AT, CREATE_DT)
			VALUES (#{msgId}, #{taskType}, 'PENDING', #{data}, NULL, NULL, CURRENT_TIMESTAMP())
			""")
	void insertMessage(TaskMessage message);

	@Delete("""
			DELETE FROM AI_PROCESSING_QUEUE
			WHERE  STATUS = 'FAILED'
			AND    CREATE_DT < DATE_SUB(NOW(), INTERVAL 90 DAY)
			""")
	void deleteOldFailed();

	@Insert("""
			INSERT IGNORE INTO ALERT_RULE (MSGID, `DATA`, CREATE_DT)
			VALUES (#{msgId}, #{data}, CURRENT_TIMESTAMP())
			""")
	void insertAlertRule(AlertMessage message);

	@Insert("""
			INSERT IGNORE INTO UI_DELETE_HISTORY (DELETE_DATE, DELETE_TYPE, DELETE_COUNT, DELETE_TIME)
			VALUES (#{deleteDate}, #{deleteType}, #{deleteCount}, CURRENT_TIMESTAMP())
			""")
	void insertDeleteHistory(DeleteMessage message);
}