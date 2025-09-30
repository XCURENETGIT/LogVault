package com.xcurenet.common.sftp;

import com.jcraft.jsch.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

@Log4j2
public class SftpFileDownloader {
	private final String username;
	private final String host;
	private final int port;
	private final String password;

	public SftpFileDownloader(String username, String host, int port, String password) {
		this.username = username;
		this.host = host;
		this.port = port;
		this.password = password;
	}

	public void downloadFile(String remoteFilePath, String localFilePath, boolean gzip) {
		Session session = null;
		ChannelSftp channelSftp = null;
		try {
			JSch jsch = new JSch();
			session = jsch.getSession(username, host, port);
			session.setPassword(password);

			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();

			Channel channel = session.openChannel("sftp");
			channel.connect();
			channelSftp = (ChannelSftp) channel;

			FileUtils.forceMkdir(new File(localFilePath).getParentFile());

			if (gzip) gzipDownload(remoteFilePath, localFilePath, channelSftp);
			else download(remoteFilePath, localFilePath, channelSftp);

			log.info("Download complete. {}", localFilePath);
		} catch (JSchException | SftpException | IOException e) {
			log.error("", e);
		} finally {
			if (channelSftp != null) {
				channelSftp.exit();
			}
			if (session != null) {
				session.disconnect();
			}
		}
	}

	private static void download(String remoteFilePath, String localFilePath, ChannelSftp channelSftp) throws IOException, SftpException {
		try (InputStream inputStream = channelSftp.get(remoteFilePath);
		     FileOutputStream outputStream = new FileOutputStream(localFilePath)) {
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
		}
	}

	private static void gzipDownload(String remoteFilePath, String localFilePath, ChannelSftp channelSftp) throws IOException, SftpException {
		try (InputStream inputStream = channelSftp.get(remoteFilePath);
		     GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
		     FileOutputStream outputStream = new FileOutputStream(localFilePath)) {
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
		}
	}
}