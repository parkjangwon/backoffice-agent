package org.parkjw.agent.backoffice.query;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import org.parkjw.agent.backoffice.export.CsvExporter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.json.JsonMapper;

class QueryControllerHttpTest {

	private QueryService queryService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		queryService = mock(QueryService.class);
		var noResultGuidanceService = new NoResultGuidanceService();
		var viewMapper = new QueryResultViewMapper(noResultGuidanceService);
		var controller = new QueryController(
				queryService,
				new CsvExporter(viewMapper, noResultGuidanceService),
				new NaturalLanguageResultFormatter(viewMapper, noResultGuidanceService),
				viewMapper,
				new SyncTaskExecutor());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setMessageConverters(
						new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()),
						new StringHttpMessageConverter(StandardCharsets.UTF_8))
				.build();
	}

	@Test
	void preview_whenJsonRequestIsPosted_returnsUserQueryResponse() throws Exception {
		when(queryService.preview(any(QueryRequest.class), eq("127.0.0.1"))).thenReturn(result());

		mockMvc.perform(post("/api/query/preview")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "actorId": "operator-123",
								  "role": "SCOPED",
								  "prompt": "example.org 사용자 목록",
								  "limit": 10
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.rowCount").value(1))
				.andExpect(jsonPath("$.data.columns[0]").value("이메일 주소"))
				.andExpect(jsonPath("$.data.rows[0]['이메일 주소']").value("user@example.org"));
	}

	@Test
	void query_whenTextFormatIsPosted_returnsPlainTextAnswer() throws Exception {
		when(queryService.preview(any(QueryRequest.class), eq("127.0.0.1"))).thenReturn(result());

		var response = mockMvc.perform(post("/api/query")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "actorId": "operator-123",
								  "role": "SCOPED",
								  "prompt": "example.org 사용자 목록",
								  "responseFormat": "TEXT",
								  "limit": 10
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		org.assertj.core.api.Assertions.assertThat(response)
				.contains("조회 결과는 총 1건입니다.")
				.contains("user@example.org");
	}

	@Test
	void query_whenCsvFormatIsPosted_returnsCsvDownload() throws Exception {
		when(queryService.export(any(QueryRequest.class), eq("127.0.0.1"))).thenReturn(result());

		var response = mockMvc.perform(post("/api/query")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "actorId": "operator-123",
								  "role": "SCOPED",
								  "prompt": "example.org 사용자 목록",
								  "responseFormat": "CSV",
								  "limit": 10
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ai-query-export.csv\""))
				.andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv")))
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		org.assertj.core.api.Assertions.assertThat(response)
				.contains("이메일 주소")
				.contains("user@example.org");
	}

	@Test
	void query_whenStreamFormatIsPosted_returnsSseDeltas() throws Exception {
		when(queryService.preview(any(QueryRequest.class), eq("127.0.0.1"))).thenReturn(result());

		MvcResult result = mockMvc.perform(post("/api/query")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "actorId": "operator-123",
								  "role": "SCOPED",
								  "prompt": "example.org 사용자 목록",
								  "responseFormat": "STREAM",
								  "limit": 10
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
				.andExpect(request().asyncStarted())
				.andReturn();

		var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		org.assertj.core.api.Assertions.assertThat(response)
				.contains("event:delta")
				.contains("data:{\"text\":\"조\"}")
				.contains("event:done")
				.contains("data:[DONE]");
	}

	private QueryResult result() {
		var row = new LinkedHashMap<String, Object>();
		row.put("email", "user@example.org");
		return new QueryResult(
				"select email from account",
				List.of("email"),
				List.of(row),
				1);
	}
}
