package org.parkjw.agent.backoffice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class IpAllowlistTest {

	private final IpAllowlist allowlist = new IpAllowlist();

	@Test
	void allows_whenExactIpMatches() {
		// given
		var rules = List.of("127.0.0.1");

		// when
		var allowed = allowlist.allows("127.0.0.1", rules);

		// then
		assertThat(allowed).isTrue();
	}

	@Test
	void allows_whenIpv4CidrMatches() {
		// given
		var rules = List.of("192.168.10.0/24");

		// when
		var allowed = allowlist.allows("192.168.10.42", rules);

		// then
		assertThat(allowed).isTrue();
	}

	@Test
	void allows_whenIpIsOutsideCidr() {
		// given
		var rules = List.of("192.168.10.0/24");

		// when
		var allowed = allowlist.allows("192.168.11.42", rules);

		// then
		assertThat(allowed).isFalse();
	}
}
