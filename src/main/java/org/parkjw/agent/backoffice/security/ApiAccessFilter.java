package org.parkjw.agent.backoffice.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.parkjw.agent.backoffice.config.AiQueryProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class ApiAccessFilter extends OncePerRequestFilter {

	private static final String API_KEY_HEADER = "X-API-Key";
	private static final String SIGNATURE_HEADER = "X-Agent-Signature";
	private static final String TIMESTAMP_HEADER = "X-Agent-Timestamp";

	private final AiQueryProperties properties;
	private final IpAllowlist ipAllowlist;
	private final Supplier<Instant> now;

	@Autowired
	public ApiAccessFilter(AiQueryProperties properties, IpAllowlist ipAllowlist) {
		this(properties, ipAllowlist, Instant::now);
	}

	ApiAccessFilter(AiQueryProperties properties, IpAllowlist ipAllowlist, Supplier<Instant> now) {
		this.properties = properties;
		this.ipAllowlist = ipAllowlist;
		this.now = now;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		var remoteIp = request.getRemoteAddr();
		if (!ipAllowlist.allows(remoteIp, properties.security().allowedIps())) {
			log.warn("ai-query api rejected reason=ip remoteIp={} path={}", remoteIp, request.getRequestURI());
			response.sendError(HttpStatus.FORBIDDEN.value(), "IP is not allowed.");
			return;
		}
		if (properties.security().apiKeyEnabled() && !validApiKey(request.getHeader(API_KEY_HEADER))) {
			log.warn("ai-query api rejected reason=api-key remoteIp={} path={}", remoteIp, request.getRequestURI());
			response.sendError(HttpStatus.UNAUTHORIZED.value(), "API key is required.");
			return;
		}
		if (!properties.security().requestSigningEnabled()) {
			filterChain.doFilter(request, response);
			return;
		}
		var wrapped = new CachedBodyRequest(request);
		if (!validSignature(wrapped)) {
			log.warn("ai-query api rejected reason=request-signature remoteIp={} path={}", remoteIp, request.getRequestURI());
			response.sendError(HttpStatus.UNAUTHORIZED.value(), "Request signature is required.");
			return;
		}
		filterChain.doFilter(wrapped, response);
	}

	private boolean validApiKey(String apiKey) {
		return apiKey != null && properties.security().apiKeys().contains(apiKey);
	}

	private boolean validSignature(CachedBodyRequest request) {
		var timestamp = request.getHeader(TIMESTAMP_HEADER);
		var signature = request.getHeader(SIGNATURE_HEADER);
		if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
			return false;
		}
		var epochSecond = parseEpochSecond(timestamp);
		if (epochSecond == null || Math.abs(now.get().getEpochSecond() - epochSecond) > properties.security().requestSigningToleranceSeconds()) {
			return false;
		}
		var expected = signature(timestamp, request.getMethod(), requestTarget(request), request.body());
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
	}

	private Long parseEpochSecond(String timestamp) {
		try {
			return Long.parseLong(timestamp);
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private String signature(String timestamp, String method, String path, byte[] body) {
		try {
			var payload = timestamp + "\n" + method + "\n" + path + "\n" + sha256(body);
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(properties.security().requestSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to verify request signature.", exception);
		}
	}

	private String requestTarget(HttpServletRequest request) {
		var query = request.getQueryString();
		if (query == null || query.isBlank()) {
			return request.getRequestURI();
		}
		return request.getRequestURI() + "?" + query;
	}

	private String sha256(byte[] body) {
		try {
			return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 digest is not available.", exception);
		}
	}

	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		private CachedBodyRequest(HttpServletRequest request) throws IOException {
			super(request);
			this.body = request.getInputStream().readAllBytes();
		}

		private byte[] body() {
			return body.clone();
		}

		@Override
		public ServletInputStream getInputStream() {
			var inputStream = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public boolean isFinished() {
					return inputStream.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener readListener) {
					throw new UnsupportedOperationException("Async request body reading is not supported.");
				}

				@Override
				public int read() {
					return inputStream.read();
				}
			};
		}
	}
}
