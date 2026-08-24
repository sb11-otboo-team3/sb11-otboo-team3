package com.otboo.domain.recommendation.llm;

import com.otboo.domain.recommendation.llm.dto.OpenRouterChatRequest;
import com.otboo.domain.recommendation.llm.dto.OpenRouterChatResponse;
import com.otboo.domain.recommendation.llm.dto.OpenRouterMessage;
import com.otboo.domain.recommendation.llm.dto.OpenRouterResponseFormat;
import com.otboo.domain.recommendation.llm.exception.LlmRequestFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
public class LlmOutfitClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public LlmOutfitClient(WebClient webClient, String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenRouter API 키가 비어 있습니다.");
        }
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    public Mono<OpenRouterChatResponse> requestCompletion(List<OpenRouterMessage> messages) {
        OpenRouterChatRequest request = new OpenRouterChatRequest(model, messages, OpenRouterResponseFormat.jsonObject());
        log.debug("OpenRouter 요청 시작, model={}", model);

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenRouterChatResponse.class)
                .onErrorMap(Exception.class, e -> {
                    log.error("OpenRouter 요청 실패", e);
                    return new LlmRequestFailedException();
                });
    }
}
