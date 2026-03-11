package com.xcurenet.logvault.job.delete;

import lombok.Data;
import org.apache.ibatis.type.Alias;

@Data
@Alias("DeleteMessage")
public class DeleteMessage {
	private String deleteDate;
	private String deleteType;
	private long deleteCount;
}
