package com.xcurenet.logvault.opensearch;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.Date;

@Data
@Document(indexName = "shadow-ai", writeTypeHint = WriteTypeHint.FALSE)
public class ShadowAiDoc {
    @Id
    @Field("id")
    private String id;

    @Field(name = "@timestamp", type = FieldType.Date, format = DateFormat.epoch_millis)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private Date timestamp;

    @Field("ctime")
    private String ctime;

    @Field("service")
    private Service service;

    @Field("user")
    private EmassDoc.User user;

    @Data
    public static class Service {
        @Field("host")
        private String host;

        @Field("category_group_cd")
        private String categoryGroupSeq;

        @Field("category_cd")
        private String categoryCd;
    }
}
