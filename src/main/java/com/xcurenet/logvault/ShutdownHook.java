package com.xcurenet.logvault;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ShutdownHook extends Thread {

	public void run() {
		log.info("★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
		log.info("window only click in this console and press Enter to call");
		log.info("★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");

		try {
			int i = System.in.read();
			log.debug("i : {}", i);
		} catch (Exception e) {
			log.error("", e);
		}
		System.exit(0);
	}
}
