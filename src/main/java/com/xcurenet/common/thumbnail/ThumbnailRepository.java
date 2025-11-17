package com.xcurenet.common.thumbnail;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ThumbnailRepository {

	@Select("""
			SELECT 	COUNT(1) AS COUNT
			FROM 	AI_THUMBNAIL_STORE
			WHERE 	HASH = #{hash}
			""")
	int isExistThumbnail(@Param("hash") String hash);

	@Insert("""
			INSERT IGNORE INTO AI_THUMBNAIL_STORE (HASH, BASE64, CREATE_DT)
			VALUES(#{hash}, #{base64}, current_timestamp())
			""")
	void insertThumbnail(@Param("hash") String hash, @Param("base64") String base64);


	@Delete("""
			DELETE
			FROM	AI_THUMBNAIL_STORE
			WHERE 	CREATE_DT < DATE_SUB(NOW(), INTERVAL #{day} DAY);
			""")
	void deleteThumbnail(@Param("day") int hash);
}
