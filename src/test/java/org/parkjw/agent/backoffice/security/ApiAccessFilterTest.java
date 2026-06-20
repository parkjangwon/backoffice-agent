package org.parkjw.agent.backoffice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.parkjw.agent.backoffice.common.LogFingerprints;
import org.parkjw.agent.backoffice.config.AiQueryProperties;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiAccessFilterTest {

	@Test
	void doFilter_whenApiKeyIsMissing() throws Exception {
		// given
		var filter = filter(List.of("127.0.0.1"));
		var request = request("127.0.0.1");
		var response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, new MockFilterChain());

		// then
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void doFilter_whenIpIsBlocked() throws Exception {
		// given
		var filter = filter(List.of("10.0.0.0/8"));
		var request = request("192.168.0.10");
		request.addHeader("X-API-Key", "test-key");
		var response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, new MockFilterChain());

		// then
		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	void doFilter_whenForwardedForClaimsAllowedIpButRemoteIpIsBlocked_rejectsRequest() throws Exception {
		// given
		var filter = filter(List.of("127.0.0.1"));
		var request = request("203.0.113.10");
		request.addHeader("X-Forwarded-For", "127.0.0.1");
		request.addHeader("X-API-Key", "test-key");
		var response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, new MockFilterChain());

		// then
		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	void doFilter_whenApiKeyAndIpAreAllowed() throws Exception {
		// given
		var filter = filter(List.of("192.168.0.0/24"));
		var request = request("192.168.0.10");
		request.addHeader("X-API-Key", "test-key");
		var response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, new MockFilterChain());

		// then
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void doFilter_whenRequestSigningIsEnabledAndSignatureIsMissing_rejectsBeforeController() throws Exception {
		// given
		var filter = signedFilter();
		var request = request("127.0.0.1", queryBody(AccessRoleBody.GLOBAL));
		request.addHeader("X-API-Key", "test-key");
		request.addHeader("X-Agent-Timestamp", "1781884800");
		var response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, new MockFilterChain());

		// then
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void doFilter_whenRequestSigningIsEnabledAndSignatureIsValid_allowsControllerToReadBody() throws Exception {
		// given
		var filter = signedFilter();
		var body = queryBody(AccessRoleBody.SCOPED);
		var request = request("127.0.0.1", body);
		request.addHeader("X-API-Key", "test-key");
		request.addHeader("X-Agent-Timestamp", "1781884800");
		request.addHeader("X-Agent-Signature", signature("1781884800", request.getMethod(), request.getRequestURI(), body));
		var response = new MockHttpServletResponse();
		FilterChain chain = (servletRequest, servletResponse) -> {
			servletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
			servletResponse.getWriter().write(new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
		};

		// when
		filter.doFilter(request, response, chain);

		// then
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(body);
	}

	@Test
	void doFilter_whenSignedGetRequestHasEmptyBody_allowsRequest() throws Exception {
		// given
		var filter = signedFilter();
		var request = new MockHttpServletRequest("GET", "/api/audit");
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("X-API-Key", "test-key");
		request.addHeader("X-Agent-Timestamp", "1781884800");
		request.addHeader("X-Agent-Signature", signature("1781884800", request.getMethod(), request.getRequestURI(), ""));
		var response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, new MockFilterChain());

		// then
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void doFilter_whenSignedRequestQueryStringIsChanged_rejectsRequest() throws Exception {
		// given
		var filter = signedFilter();
		var body = queryBody(AccessRoleBody.SCOPED);
		var request = new MockHttpServletRequest("POST", "/api/query/export");
		request.setQueryString("format=json");
		request.setRemoteAddr("127.0.0.1");
		request.setContentType("application/json");
		request.setCharacterEncoding(StandardCharsets.UTF_8.name());
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		request.addHeader("X-API-Key", "test-key");
		request.addHeader("X-Agent-Timestamp", "1781884800");
		request.addHeader("X-Agent-Signature", signature("1781884800", request.getMethod(), "/api/query/export?format=csv", body));
		var response = new MockHttpServletResponse();

		// when
		filter.doFilter(request, response, new MockFilterChain());

		// then
		assertThat(response.getStatus()).isEqualTo(401);
	}

	private ApiAccessFilter filter(List<String> allowedIps) {
		var properties = new AiQueryProperties(
				new AiQueryProperties.Security(true, List.of("test-key"), allowedIps, false, "", 300),
				null,
					null,
					null,
					null,
					null,
					null);
		return new ApiAccessFilter(properties, new IpAllowlist());
	}

	private ApiAccessFilter signedFilter() {
		var properties = new AiQueryProperties(
				new AiQueryProperties.Security(true, List.of("test-key"), List.of("127.0.0.1"), true, "signing-secret", 300),
				null,
					null,
					null,
					null,
					null,
					null);
		return new ApiAccessFilter(properties, new IpAllowlist(), () -> Instant.ofEpochSecond(1781884800));
	}

	private MockHttpServletRequest request(String remoteIp) {
		var request = new MockHttpServletRequest("POST", "/api/query/preview");
		request.setRemoteAddr(remoteIp);
		return request;
	}

	private MockHttpServletRequest request(String remoteIp, String body) {
		var request = request(remoteIp);
		request.setContentType("application/json");
		request.setCharacterEncoding(StandardCharsets.UTF_8.name());
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		return request;
	}

	private String queryBody(AccessRoleBody role) {
		return """
				{"actorId":"operator-123","role":"%s","prompt":"사용자 수","responseFormat":"TEXT","limit":10}
				""".formatted(role.name()).trim();
	}

	private String signature(String timestamp, String method, String path, String body) throws Exception {
		var payload = timestamp + "\n" + method + "\n" + path + "\n" + bodyHash(body);
		var mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("signing-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		var bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		var builder = new StringBuilder(bytes.length * 2);
		for (var value : bytes) {
			builder.append("%02x".formatted(value & 0xff));
		}
		return "sha256=" + builder;
	}

	private String bodyHash(String body) throws Exception {
		var digest = java.security.MessageDigest.getInstance("SHA-256")
				.digest(body.getBytes(StandardCharsets.UTF_8));
		var builder = new StringBuilder(digest.length * 2);
		for (var value : digest) {
			builder.append("%02x".formatted(value & 0xff));
		}
		return "sha256:" + builder;
	}

	private enum AccessRoleBody {
		GLOBAL,
		SCOPED
	}
}
