package com.xcurenet.common.thumbnail;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ThumbnailRepository {

	@Select("""
			SELECT 	COUNT(1) AS count
			FROM 	AI_THUMBNAIL_STORE
			WHERE 	HASH = #{hash}
			""")
	int isExistThumbnail(@Param("hash") String hash);

	@Insert("""
			INSERT IGNORE INTO AI_THUMBNAIL_STORE (HASH, BASE64, CREATE_DT)
			VALUES(#{hash}, #{base64}, current_timestamp())
			""")
	void insertThumbnail(@Param("hash") String hash, @Param("base64") String base64);
}
