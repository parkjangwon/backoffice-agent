package org.parkjw.agent.backoffice.catalog;

import org.parkjw.agent.backoffice.common.ApiResponse;
import org.parkjw.agent.backoffice.config.AiQueryProperties;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

	private final SchemaScanner scanner;
	private final CatalogRepository repository;
	private final AiQueryProperties properties;

	public CatalogController(SchemaScanner scanner, CatalogRepository repository, AiQueryProperties properties) {
		this.scanner = scanner;
		this.repository = repository;
		this.properties = properties;
	}

	@PostMapping("/scan")
	public ApiResponse<CatalogStatusResponse> scan(@RequestBody(required = false) CatalogScanRequest request) {
		var snapshot = request == null ? scanner.scan() : scanner.scan(request);
		return ApiResponse.ok(CatalogStatusResponse.from(true, repository.save(snapshot), properties));
	}

	@GetMapping("/status")
	public ApiResponse<CatalogStatusResponse> status() {
		return ApiResponse.ok(CatalogStatusResponse.from(repository.hasStoredSnapshot(), repository.current(), properties));
	}

	@GetMapping
	public ApiResponse<CatalogStatusResponse> current() {
		return status();
	}
}
