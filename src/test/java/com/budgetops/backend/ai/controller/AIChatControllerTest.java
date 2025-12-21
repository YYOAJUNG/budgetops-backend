package com.budgetops.backend.ai.controller;

import com.budgetops.backend.ai.dto.ChatRequest;
import com.budgetops.backend.ai.dto.ChatResponse;
import com.budgetops.backend.ai.service.AIChatService;
import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.oauth.filter.JwtAuthenticationFilter;
import com.budgetops.backend.oauth.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AIChatController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
        })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("AIChatController 테스트")
class AIChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AIChatService aiChatService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private AdminAuthUtil adminAuthUtil;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private AuditingHandler auditingHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("채팅 요청 성공")
    void chat_Success() throws Exception {
        // given
        ChatRequest request = new ChatRequest();
        request.setSessionId("test-session");
        request.setMessage("비용 최적화 방법을 알려주세요");

        ChatResponse response = ChatResponse.builder()
                .response("비용 최적화를 위해 다음과 같은 방법을 추천합니다...")
                .sessionId("test-session")
                .build();

        given(aiChatService.chat(any(ChatRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("test-session"))
                .andExpect(jsonPath("$.response").exists());
    }

    @Test
    @DisplayName("채팅 요청 실패 - RuntimeException")
    void chat_RuntimeException() throws Exception {
        // given
        ChatRequest request = new ChatRequest();
        request.setSessionId("test-session");
        request.setMessage("비용 최적화 방법을 알려주세요");

        given(aiChatService.chat(any(ChatRequest.class)))
                .willThrow(new RuntimeException("토큰이 부족합니다"));

        // when & then
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.sessionId").value("test-session"))
                .andExpect(jsonPath("$.response").exists());
    }

    @Test
    @DisplayName("헬스 체크")
    void health_Success() throws Exception {
        // when & then
        mockMvc.perform(get("/api/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("AI Chat"));
    }
}

