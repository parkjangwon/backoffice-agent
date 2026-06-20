package org.parkjw.agent.backoffice.audit;

import java.util.List;

import org.parkjw.agent.backoffice.common.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

	private final AuditService auditService;

	public AuditController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<List<AuditEvent>> recent() {
		return ApiResponse.ok(auditService.recent());
	}
}
