package com.budgetops.backend.ai.controller;

import com.budgetops.backend.ai.dto.ChatRequest;
import com.budgetops.backend.ai.dto.ChatResponse;
import com.budgetops.backend.ai.service.AIChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIChatController {
    
    private final AIChatService aiChatService;
    
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Received chat request: sessionId={}, message length={}", 
                request.getSessionId(), request.getMessage().length());
        
        try {
            ChatResponse response = aiChatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to process chat request: {}", e.getMessage(), e);
            // 사용자 친화적인 에러 메시지 반환
            ChatResponse errorResponse = ChatResponse.builder()
                    .response("죄송합니다. AI 응답 생성 중 오류가 발생했습니다: " + e.getMessage() + 
                            "\n\n잠시 후 다시 시도해주세요.")
                    .sessionId(request.getSessionId())
                    .build();
            return ResponseEntity.status(500).body(errorResponse);
        } catch (Exception e) {
            log.error("Unexpected error processing chat request", e);
            ChatResponse errorResponse = ChatResponse.builder()
                    .response("죄송합니다. 예기치 않은 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
                    .sessionId(request.getSessionId())
                    .build();
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "AI Chat"));
    }
}

