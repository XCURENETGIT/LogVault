package com.xcurenet.common.geo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Location;
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
public class GeoLocation {
	public final static Double KR_LATITUDE = 37.5658;
	public final static Double KR_LONGITUDE = 126.978;
	public final static Double EN_LATITUDE = 40.85546193768166;
	public final static Double EN_LONGITUDE = -80.86218744961558;

	private final DatabaseReader dbReader;
	private final DatabaseReader databaseCityReader;

	@Autowired
	public GeoLocation(@Qualifier("databaseReader") DatabaseReader dbReader, @Qualifier("databaseCityReader") DatabaseReader databaseCityReader) {
		this.dbReader = dbReader;
		this.databaseCityReader = databaseCityReader;
	}


	@PreDestroy
	public void destroy() throws IOException {
		dbReader.close();
		databaseCityReader.close();
	}

	public Location getLocation(IP ip) {
		return getLocation(ip, KR_LATITUDE, KR_LONGITUDE);
	}

	/**
	 * 주어진 IP에 해당하는 GEO 좌표를 반환한다.
	 */
	public Location getLocation(IP ip, Double defaultLat, Double defaultLng) {
		try {
			Location location = checkLocationForIp(ip, defaultLat, defaultLng);
			if (location != null) return location;

			InetAddress ipAddress = InetAddress.getByName(ip.toString());
			CityResponse city = databaseCityReader.city(ipAddress);
			if (city.getLocation().getLatitude() != null) return city.getLocation();
		} catch (Exception e) {
			log.debug("", e);
		}
		return new Location(0, 0, defaultLat, defaultLng, 0, 0, null);
	}

	/**
	 * 주어진 IP에 해당하는 국가코드를 반환한다.
	 */
	public String getCountryCode(IP ip) {
		try {
			return checkCountryForIp(ip);
		} catch (Exception e) {
			log.debug("", e);
			return null;
		}
	}

	private String checkCountryForIp(IP ip) throws IOException, GeoIp2Exception {
		if (isPrivateIp(ip.toString())) return "LX";

		try {
			InetAddress ipAddress = InetAddress.getByName(ip.toString());
			CountryResponse response = dbReader.country(ipAddress);
			if (response.getCountry().getIsoCode() != null) return response.getCountry().getIsoCode();
		} catch (Exception e) {
			log.debug("", e);
		}
		return "EN";
	}

	private boolean isPrivateIp(String ip) {
		return ip.startsWith("0.") || ip.startsWith("127.") || ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("192.168.") || (ip.startsWith("172.") && Integer.parseInt(ip.split("\\.")[1]) >= 16 && Integer.parseInt(ip.split("\\.")[1]) <= 31);
	}

	private Location checkLocationForIp(IP ip, Double defaultLat, Double defaultLng) {
		if (isPrivateIp(ip.toString())) return new Location(0, 0, defaultLat, defaultLng, 0, 0, null);
		return isLocationIp(ip.toString());
	}

	private Location isLocationIp(String ip) {
		if (ip.startsWith("172.64") || ip.startsWith("160.79")) return new Location(0, 0, EN_LATITUDE, EN_LONGITUDE, 0, 0, null);
		return null;
	}
}
