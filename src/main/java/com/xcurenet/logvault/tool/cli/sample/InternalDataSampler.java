package com.xcurenet.logvault.tool.cli.sample;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;
import com.xcurenet.common.types.IP;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bson.Document;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.concurrent.Callable;

@Log4j2
@CommandLine.Command(name = "generator", description = "사내 데이터를 이용한 샘플 데이터 생성기")
public class InternalDataSampler implements Callable<Integer> {
	private static final String MONGO_URL = "mongodb://emassailt:27018/venus?replicaSet=shard1rs";
	private static final String ES_URL = "http://emassailt:9200/edc_w_%s/_search";
	private static final String ES_QUERY = """
			{
				"size": 1,
				"query": {"query_string": {"query": "+svc1:I AND ctime:{%s TO *} %s"}},
				"sort": [{ "ctime": { "order": "asc" } }]
			}
			""";
	private static final String DECODER_INFO = "/users/las/msg/info/wmail";
	private static final String DECODER_DATA = "/users/las/msg/data";
	private final Path lastData = Paths.get("./sample/last");

	@Override
	public Integer call() {
		while (true) {
			try {
				LastData data = getLastData();
				JSONObject source = query(data.ctime, data.msgId);
				if (source == null) {
					Common.sleep(5000);
					continue;
				}
				JSONObject doc = getMongoData(source.getString("msgid"));
				System.out.println(doc);
				writeMsg(doc, data.ctime);

				writeLastData(source.getString("msgid"), source.getString("ctime"));
				Common.sleep(500);
			} catch (Exception e) {
				log.error("", e);
				break;
			}
		}
		return 0;
	}

	private JSONObject query(final String ctime, final String lastMsgId) {
		try {
			String msgId = "";
			if (lastMsgId != null) msgId = " AND NOT msgid:" + lastMsgId;

			Connection.Response response = Jsoup.connect(String.format(ES_URL, ctime.substring(0, 6))).header("Content-Type", "application/json").ignoreContentType(true).method(Connection.Method.POST).requestBody(String.format(ES_QUERY, ctime, msgId)).execute();

			JSONObject obj = JSONObject.parseObject(response.body());
			JSONArray list = obj.getJSONObject("hits").getJSONArray("hits");
			if (list.isEmpty()) return null;

			return list.getJSONObject(0).getJSONObject("_source");
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	private JSONObject getMongoData(final String msgId) {
		try (MongoClient mongoClient = MongoClients.create(MONGO_URL)) {
			MongoDatabase db = mongoClient.getDatabase("venus");
			MongoCollection<Document> col = db.getCollection("EMS_MESSAGE_" + msgId.substring(0, 6));
			return col.find(Filters.eq("_id", msgId), JSONObject.class).first();
		}
	}


	private void writeLastData(final String msgId, final String ctime) throws IOException {
		Files.writeString(lastData, String.format("%s,%s", msgId, ctime), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private LastData getLastData() throws IOException {
		if (Files.exists(lastData)) {
			final String text = Files.readString(lastData);
			return new LastData(text.split(",")[0], text.split(",")[1]);
		} else {
			return new LastData(null, "20250701010101");
		}
	}

	private void writeMsg(final JSONObject doc, final String ctime) throws Exception {
		String msgId = doc.getString("_id");
		IP srcIp = new IP(doc.getJSONObject("network").getString("srcIp"));
		IP dstIp = new IP(doc.getJSONObject("network").getString("dstIp"));
		int sPort = doc.getJSONObject("network").getInteger("srcPort");
		String header = getName(ctime, srcIp, dstIp, sPort, 443, "hdr");
		String msg = getName(ctime, srcIp, dstIp, sPort, 443, "txt");

		StringBuilder sb = new StringBuilder();
		sb.append("[WMAIL]").append("\n");
		sb.append("CTIME : ").append(DateUtils.convertDateTimeYYYYMMDD(ctime)).append("\n");
		sb.append("SOURCEIP : ").append(srcIp).append("\n");
		sb.append("DESTINATIONIP : ").append(dstIp).append("\n");
		sb.append("SOURCEPORT : ").append(sPort).append("\n");
		sb.append("HOST : ").append(doc.getJSONObject("http").getString("host")).append("\n");
		if (doc.getJSONObject("http").getString("path") != null)
			sb.append("URL : ").append(doc.getJSONObject("http").getString("path")).append("\n");
		if (doc.getJSONObject("http").getString("query") != null)
			sb.append("QUERY : ").append(doc.getJSONObject("http").getString("query")).append("\n");

		if (doc.getJSONObject("http").get("header") != null) {
			sb.append("HDRFILE : ").append(header).append("\n");
			Files.writeString(Paths.get(getPath(header)), doc.getJSONObject("http").getString("header"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}

		if (doc.getJSONObject("body").getInteger("size") > 0) {
			sb.append("MSGFILE : ").append(msg).append("\n");
			bodyDown(msgId, Paths.get(getPath(msg)));
		}

		sb.append("SUBJECT : ").append(doc.getString("subject")).append("\n");
		if(doc.getJSONObject("network").get("protocol") != null)
			sb.append("PROTOCOL : ").append(doc.getJSONObject("network").getString("protocol")).append("\n");

		sb.append("STYPE : ").append(doc.getString("svc")).append("\n");
		if (doc.get("rootMtr") != null) sb.append("ROOTMTR : ").append(doc.getString("rootMtr")).append("\n");

		JSONArray files = doc.getJSONArray("attach");
		for (int i = 0; i < files.size(); i++) {
			JSONObject file = files.getJSONObject(i);
			sb.append("PCFILE[").append(i).append("] : ").append(file.getString("name")).append("\n");
			if (file.get("path") != null) {
				String path = Paths.get(file.getString("path")).getFileName().toString();
				sb.append("APPFILE[").append(i).append("] : ").append(path).append("\n");

				Path src = Paths.get(path);
				Path target = Paths.get(getPath(src.getFileName().toString()));
				attachDown(src.toString(), target);
			}
			String nameExist = file.getString("nameExist").equals("Y") ? "1" : "0";
			String ext = file.getString("ext") == null ? "unknown" : file.getString("ext");
			String extension = String.format("%s|%s|SYNAP||0", nameExist, ext);
			sb.append("EXTENSION[").append(i).append("] : ").append(extension).append("\n");
		}

		String path = getInfoPath(doc.getString("fileName"));
		Path file = Paths.get(path);
		Files.writeString(file, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		Common.setAllPermissions(file.toFile());
		log.info("WRITE_INFO | {}", file);
	}

	private String getName(String ctime, IP srcIp, IP dstIp, int srcPort, int dstPort, String ext) throws IOException {
		return String.format("%s-%s-%s-%s-%s-00-462358-SAMPLE-LOCAL-http-1-0.%s", ctime, srcIp.toHexString(), dstIp.toHexString(), srcPort, dstPort, ext);
	}

	private void bodyDown(final String msgId, final Path target) {
		try (MongoClient mongoClient = MongoClients.create(MONGO_URL)) {
			MongoDatabase db = mongoClient.getDatabase("venus");
			String bucketName = "EMS_BODY_" + msgId.substring(0, 6);
			GridFSBucket bucket = GridFSBuckets.create(db, bucketName);
			GridFSFile gridFSFile = bucket.find(Filters.eq("filename", msgId + ".body")).first();
			if (gridFSFile == null) {
				log.info("NOT_FOUND_BODY: {} {}", bucketName, msgId);
				return;
			}

			try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				bucket.downloadToStream(gridFSFile.getObjectId(), out);
				log.info("DOWNLOAD_BODY | {}", target);
			} catch (IOException e) {
				log.error("", e);
			}
		}
	}


	private void attachDown(final String path, final Path target) {
		try (MinioClient minioClient = MinioClient.builder().endpoint("http://emassailt:19000").credentials("minioadmin", "minioadmin").build(); InputStream in = minioClient.getObject(GetObjectArgs.builder().bucket("emass").object(path).build())) {
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			log.warn("attachDown | Error downloading file {}", path, e);
		}
	}

	public String getPath(final String fileName) {
		if (Common.isEmpty(fileName)) return null;
		return Common.makeFilepath("/users/las/msg/data", Long.toString(Common.getSplitNum(fileName, 100)), fileName);
	}

	public String getInfoPath(final String fileName) {
		if (Common.isEmpty(fileName)) return null;
		return Common.makeFilepath("/users/las/msg/info/wmail", Long.toString(Common.getSplitNum(fileName, 100)), fileName);
	}

	@AllArgsConstructor
	private static class LastData {
		private String msgId;
		private String ctime;
	}
}
