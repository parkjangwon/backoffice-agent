package org.parkjw.agent.backoffice.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class LogFingerprints {

	private LogFingerprints() {
	}

	public static String sha256(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return "sha256:" + HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest is not available.", exception);
		}
	}
}
