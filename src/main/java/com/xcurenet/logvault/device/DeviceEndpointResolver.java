package com.xcurenet.logvault.device;

import com.xcurenet.logvault.loader.mapper.InfoLoaderMapper;
import com.xcurenet.logvault.loader.type.DeviceNodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class DeviceEndpointResolver {

    public static final String HOST_PLACEHOLDER = "{device.host}";

    private final InfoLoaderMapper mapper;

    public List<DeviceNodeVO> getNodes(String infoKey) {
        if (!StringUtils.hasText(infoKey)) {
            throw new IllegalArgumentException("infoKey must not be blank");
        }
        return mapper.getDeviceNodesByInfoKey(infoKey);
    }

    public String resolveHost(String configuredHost, String infoKey) {
        List<String> hosts = resolveHosts(configuredHost, infoKey);
        if (hosts.size() > 1) {
            log.warn("Multiple active device mappings found for INFO_KEY '{}'. Using the first host: {}", infoKey, hosts.get(0));
        }
        return hosts.get(0);
    }

    public List<String> resolveHosts(String configuredHost, String infoKey) {
        List<DeviceNodeVO> nodes = getNodes(infoKey);
        if (nodes == null || nodes.isEmpty()) {
            return List.of(fallbackValue(infoKey, configuredHost));
        }
        return nodes.stream()
                .map(DeviceNodeVO::getDeviceIp)
                .map(this::normalizeHost)
                .map(host -> replaceHost(configuredHost, host))
                .distinct()
                .toList();
    }

    public String resolveConfiguredUrl(String configuredUrl, String infoKey) {
        List<String> urls = resolveConfiguredUrls(configuredUrl, infoKey);
        if (urls.size() > 1) {
            log.warn("Multiple active device mappings found for INFO_KEY '{}'. Using the first endpoint: {}", infoKey, urls.get(0));
        }
        return urls.get(0);
    }

    public List<String> resolveConfiguredUrls(String configuredUrl, String infoKey) {
        List<DeviceNodeVO> nodes = getNodes(infoKey);
        if (nodes == null || nodes.isEmpty()) {
            return List.of(fallbackValue(infoKey, configuredUrl));
        }
        return nodes.stream()
                .map(DeviceNodeVO::getDeviceIp)
                .map(this::normalizeHost)
                .map(host -> replaceHost(configuredUrl, host))
                .distinct()
                .toList();
    }

    private String replaceHost(String configuredValue, String host) {
        if (!StringUtils.hasText(configuredValue)) {
            throw new IllegalStateException("API endpoint configuration must not be blank");
        }
        if (configuredValue.contains(HOST_PLACEHOLDER)) {
            return configuredValue.replace(HOST_PLACEHOLDER, host);
        }
        if (!configuredValue.contains("://")) {
            return host;
        }

        try {
            URI configuredUri = new URI(configuredValue);
            return new URI(
                    configuredUri.getScheme(),
                    configuredUri.getUserInfo(),
                    host,
                    configuredUri.getPort(),
                    configuredUri.getPath(),
                    configuredUri.getQuery(),
                    configuredUri.getFragment()
            ).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid API endpoint configuration: " + configuredValue, e);
        }
    }

    private String fallbackValue(String infoKey, String fallback) {
        if (!StringUtils.hasText(fallback) || fallback.contains(HOST_PLACEHOLDER)) {
            throw new IllegalStateException("No active device mapping for INFO_KEY: " + infoKey);
        }
        log.warn("No active device mapping for INFO_KEY '{}'. Using the existing endpoint configuration.", infoKey);
        return fallback;
    }

    private String normalizeHost(String address) {
        if (!StringUtils.hasText(address)) {
            throw new IllegalStateException("UI_DEVICE_NODE.DEVICE_IP must not be blank");
        }
        String value = address.trim();
        if (!value.contains("://")) {
            return value.replaceAll("/+$", "");
        }
        try {
            URI uri = new URI(value);
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalStateException("Invalid device address: " + address);
            }
            return uri.getHost();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid device address: " + address, e);
        }
    }
}
