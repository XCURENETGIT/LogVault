package com.xcurenet.logvault.privacy.validator.service.an;

import com.xcurenet.logvault.privacy.ValidationResult;
import com.xcurenet.logvault.privacy.validator.service.DefaultCheck;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AddressValidator implements DefaultCheck {
	private static final List<String> address = new ArrayList<>();

	static {
		try {
			loadResource("address_new.txt");
			loadResource("address_org.txt");
		} catch (IOException e) {
			throw new RuntimeException("Failed to load address resources", e);
		}
	}

	private static void loadResource(String name) throws IOException {
		URL url = ResourceLoader.class.getClassLoader().getResource(name);
		if (url != null) {
			try (var in = url.openStream()) {
				address.addAll(IOUtils.readLines(in, StandardCharsets.UTF_8));
			}
		}
	}

	@Override
	public ValidationResult validate(String input, String context) {
		if (input == null) return ValidationResult.fail("Address is null");
		if (input.length() < 2) {
			return ValidationResult.fail("Input too short for address validation: " + input);
		}

		boolean match = address.stream().anyMatch(input::contains);
		if (match) return ValidationResult.ok();
		return ValidationResult.fail("Address does not contain valid keywords: " + input);
	}
}
