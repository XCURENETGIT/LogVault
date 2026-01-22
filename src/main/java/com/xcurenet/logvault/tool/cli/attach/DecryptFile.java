package com.xcurenet.logvault.tool.cli.attach;

import com.xcurenet.common.utils.Common;
import com.xcurenet.crypto.Crypto;
import com.xcurenet.logvault.tool.cli.status.IndexCommon;
import picocli.CommandLine;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "dec_file", description = "File Decryption Utility")
public class DecryptFile implements Callable<Integer> {

	@CommandLine.Option(names = {"-s", "--src"}, required = true, description = "Download path for decrypted file")
	private String src;

	@CommandLine.Option(names = {"-d", "--dst"}, required = true, description = "Output path")
	private String dst;

	@Override
	public Integer call() throws Exception {
		Path srcPath = Path.of(src);
		if (!Files.exists(srcPath)) {
			System.out.println("The src file not found.");
			return 0;
		}

		Path dstPath = Path.of(dst);
		if (!Files.isDirectory(dstPath) && Files.isRegularFile(dstPath)) {
			System.out.println("The output directory not found.");
			return 0;
		}

		if (Files.isDirectory(dstPath)) {
			dstPath = Path.of(Objects.requireNonNull(Common.makeFilepath(dstPath.toString(), srcPath.getFileName().toString())));
		}

		Crypto crypto = new Crypto(Common.hexToBytes(Common.getKey()), Crypto.CIPHER.getCipher(IndexCommon.getCipher()));
		try (FileInputStream in = new FileInputStream(srcPath.toFile()); FileOutputStream out = new FileOutputStream(dstPath.toFile())) {
			crypto.decrypt(in, out);
			System.out.println(dstPath.toFile().getAbsolutePath());
		}
		return 0;
	}
}
