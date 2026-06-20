package org.parkjw.agent.backoffice.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class IpAllowlist {

	public boolean allows(String remoteIp, List<String> rules) {
		if (rules == null || rules.isEmpty()) {
			return true;
		}
		return rules.stream().anyMatch(rule -> matches(remoteIp, rule));
	}

	private boolean matches(String remoteIp, String rule) {
		if (remoteIp.equals(rule)) {
			return true;
		}
		if (!rule.contains("/")) {
			return false;
		}
		return cidrMatches(remoteIp, rule);
	}

	private boolean cidrMatches(String remoteIp, String rule) {
		try {
			var parts = rule.split("/", 2);
			var address = InetAddress.getByName(remoteIp).getAddress();
			var network = InetAddress.getByName(parts[0]).getAddress();
			var prefixLength = Integer.parseInt(parts[1]);
			return address.length == network.length && prefixMatches(address, network, prefixLength);
		}
		catch (UnknownHostException | NumberFormatException exception) {
			return false;
		}
	}

	private boolean prefixMatches(byte[] address, byte[] network, int prefixLength) {
		if (prefixLength < 0 || prefixLength > address.length * 8) {
			return false;
		}
		var fullBytes = prefixLength / 8;
		var remainingBits = prefixLength % 8;
		if (!Arrays.equals(Arrays.copyOf(address, fullBytes), Arrays.copyOf(network, fullBytes))) {
			return false;
		}
		if (remainingBits == 0) {
			return true;
		}
		var mask = (byte) (0xFF << (8 - remainingBits));
		return (address[fullBytes] & mask) == (network[fullBytes] & mask);
	}
}
