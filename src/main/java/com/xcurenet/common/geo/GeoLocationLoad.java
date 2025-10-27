package com.xcurenet.common.geo;

import com.maxmind.geoip2.DatabaseReader;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Log4j2
@Component
public class GeoLocationLoad {

	@Value("classpath:geo/GeoLite2-Country.mmdb") // resources 디렉토리의 파일
	private Resource geoIpDBResource;

	@Value("classpath:geo/GeoLite2-ASN.mmdb") // resources 디렉토리의 파일
	private Resource geoASNDBResource;

	@Value("classpath:geo/GeoLite2-City.mmdb") // resources 디렉토리의 파일
	private Resource geoCityResource;

	@Bean
	public DatabaseReader databaseReader() throws IOException {
		try (InputStream is = geoIpDBResource.getInputStream()) {
			return new DatabaseReader.Builder(is).build();
		}
	}

	@Bean
	public DatabaseReader databaseASNReader() throws IOException {
		try (InputStream is = geoASNDBResource.getInputStream()) {
			return new DatabaseReader.Builder(is).build();
		}
	}

	@Bean
	public DatabaseReader databaseCityReader() throws IOException {
		try (InputStream is = geoCityResource.getInputStream()) {
			return new DatabaseReader.Builder(is).build();
		}
	}
}
