package com.xcurenet.logvault.module.analysis;

import com.maxmind.geoip2.record.Location;
import com.xcurenet.common.geo.GeoASNLocation;
import com.xcurenet.common.geo.GeoLocation;
import com.xcurenet.common.msg.MSGData;
import com.xcurenet.common.types.FileNameInfo;
import com.xcurenet.logvault.module.ScanData;
import com.xcurenet.logvault.opensearch.EmassDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.geo.GeoPoint;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class NetworkGEOLocation {
	private final GeoLocation geoLocation;
	private final GeoASNLocation geoASNLocation;

	public void networkGEO(final ScanData msg) {
		try {
			EmassDoc doc = msg.getEmassDoc();
			MSGData data = msg.getMsgData();
			EmassDoc.Network network = doc.getNetwork();
			if (network != null) {
				FileNameInfo fileNameInfo = msg.getFileNameInfo();
				Location srcLocation = geoLocation.getLocation(fileNameInfo.getSrcIP(), GeoLocation.KR_LATITUDE, com.xcurenet.common.geo.GeoLocation.KR_LONGITUDE);
				Location dstLocation = geoLocation.getLocation(fileNameInfo.getDstIP(), com.xcurenet.common.geo.GeoLocation.EN_LATITUDE, com.xcurenet.common.geo.GeoLocation.EN_LONGITUDE);

				network.setSrcCountry(geoLocation.getCountryCode(data.getSourceIp()));
				network.setSrcAsn(geoASNLocation.getASNCode(data.getSourceIp()));
				network.setSrcLocation(new GeoPoint(srcLocation.getLatitude(), srcLocation.getLongitude()));
				network.setDstCountry(geoLocation.getCountryCode(data.getDestinationIp()));
				network.setDstAsn(geoASNLocation.getASNCode(data.getDestinationIp()));
				network.setDstLocation(new GeoPoint(dstLocation.getLatitude(), dstLocation.getLongitude()));
				log.debug("GEO_LOCATION | {}", network);
			}
		} catch (Exception e) {
			log.warn("GEO_LOCATION | {}", e.getMessage());
		}
	}
}
