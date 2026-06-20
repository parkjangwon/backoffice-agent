package org.parkjw.agent.backoffice.query;

import java.util.stream.Stream;

import org.parkjw.agent.backoffice.common.ApiResponse;
import org.parkjw.agent.backoffice.export.CsvExporter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/query")
public class QueryController {

	private final QueryService queryService;
	private final CsvExporter csvExporter;
	private final NaturalLanguageResultFormatter textFormatter;
	private final QueryResultViewMapper viewMapper;
	private final TaskExecutor taskExecutor;

	public QueryController(
			QueryService queryService,
			CsvExporter csvExporter,
			NaturalLanguageResultFormatter textFormatter,
			QueryResultViewMapper viewMapper,
			TaskExecutor taskExecutor) {
		this.queryService = queryService;
		this.csvExporter = csvExporter;
		this.textFormatter = textFormatter;
		this.viewMapper = viewMapper;
		this.taskExecutor = taskExecutor;
	}

	@PostMapping
	public ResponseEntity<?> query(@Valid @RequestBody QueryRequest request, HttpServletRequest servletRequest) {
		var format = request.responseFormat() == null ? ResponseFormat.JSON : request.responseFormat();
		var clientIp = clientIp(servletRequest);
		return switch (format) {
			case JSON -> ResponseEntity.ok(ApiResponse.ok(viewMapper.toResponse(queryService.preview(request, clientIp))));
			case CSV -> csvResponse(queryService.export(request, clientIp));
			case TEXT -> ResponseEntity.ok()
					.contentType(MediaType.TEXT_PLAIN)
					.body(textFormatter.format(queryService.preview(request, clientIp)));
			case STREAM -> streamResponse(queryService.preview(request, clientIp));
		};
	}

	@PostMapping("/preview")
	public ApiResponse<UserQueryResponse> preview(@Valid @RequestBody QueryRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(viewMapper.toResponse(queryService.preview(request, clientIp(servletRequest))));
	}

	@PostMapping("/export")
	public ResponseEntity<String> export(
			@Valid @RequestBody QueryRequest request,
			@RequestParam(defaultValue = "csv") String format,
			HttpServletRequest servletRequest) {
		if (!"csv".equalsIgnoreCase(format)) {
			throw new SqlPolicyException("MVP export supports csv only.");
		}
		var result = queryService.export(request, clientIp(servletRequest));
		return csvResponse(result);
	}

	private ResponseEntity<String> csvResponse(QueryResult result) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ai-query-export.csv\"")
				.contentType(new MediaType("text", "csv"))
				.body(csvExporter.export(result));
	}

	private ResponseEntity<SseEmitter> streamResponse(QueryResult result) {
		var emitter = new SseEmitter();
		taskExecutor.execute(() -> {
			try {
				var chunks = streamChunks(textFormatter.format(result)).iterator();
				while (chunks.hasNext()) {
					emitter.send(SseEmitter.event()
							.name("delta")
							.data(new StreamDelta(chunks.next()), MediaType.APPLICATION_JSON));
				}
				emitter.send(SseEmitter.event()
						.name("done")
						.data("[DONE]", MediaType.TEXT_PLAIN));
				emitter.complete();
			}
			catch (Exception exception) {
				emitter.completeWithError(exception);
			}
		});
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.body(emitter);
	}

	private String clientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	private Stream<String> streamChunks(String text) {
		if (text == null || text.isEmpty()) {
			return Stream.empty();
		}
		return text.codePoints()
				.mapToObj(codePoint -> new String(Character.toChars(codePoint)));
	}

	private record StreamDelta(String text) {
	}
}
