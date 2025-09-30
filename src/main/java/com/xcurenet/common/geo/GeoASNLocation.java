package com.xcurenet.common.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.xcurenet.common.types.IP;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;

@Log4j2
@Component
public class GeoASNLocation {

	private final DatabaseReader databaseASNReader;

	@Autowired
	public GeoASNLocation(@Qualifier("databaseASNReader") DatabaseReader databaseASNReader) {
		this.databaseASNReader = databaseASNReader;
	}

	@PreDestroy
	public void destroy() throws IOException {
		databaseASNReader.close();
	}

	public String getASNCode(IP ip) {
		try {
			return checkASNForIp(ip);
		} catch (Exception e) {
			return null;
		}
	}

	private String checkASNForIp(IP ip) throws IOException, GeoIp2Exception {
		if (isPrivateIp(ip.toString())) return null;
		InetAddress ipAddress = InetAddress.getByName(ip.toString());
		return databaseASNReader.asn(ipAddress).getAutonomousSystemOrganization();
	}

	private boolean isPrivateIp(String ip) {
		return ip.startsWith("0.") || ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("192.168.") || (ip.startsWith("172.") && Integer.parseInt(ip.split("\\.")[1]) >= 16 && Integer.parseInt(ip.split("\\.")[1]) <= 31);
	}
}
