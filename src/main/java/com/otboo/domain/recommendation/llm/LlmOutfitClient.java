package com.otboo.domain.recommendation.llm;

import com.otboo.domain.recommendation.llm.dto.OpenRouterChatRequest;
import com.otboo.domain.recommendation.llm.dto.OpenRouterChatResponse;
import com.otboo.domain.recommendation.llm.dto.OpenRouterMessage;
import com.otboo.domain.recommendation.llm.dto.OpenRouterResponseFormat;
import com.otboo.domain.recommendation.llm.exception.LlmRequestFailedException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
public class LlmOutfitClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public LlmOutfitClient(WebClient webClient, String apiKey, String model) {
        this(webClient, apiKey, model, Duration.ofSeconds(15));
    }

    public LlmOutfitClient(
            WebClient webClient,
            String apiKey,
            String model,
            Duration timeout
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenRouter API 키가 비어 있습니다.");
        }

        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
    }

    public Mono<OpenRouterChatResponse> requestCompletion(List<OpenRouterMessage> messages) {
        OpenRouterChatRequest request = new OpenRouterChatRequest(
                model,
                messages,
                OpenRouterResponseFormat.jsonObject()
        );

        log.debug("OpenRouter 요청 시작, model={}", model);

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenRouterChatResponse.class)
                .timeout(timeout)
                
                .flatMap(response -> {
                    if (response.choices() == null || response.choices().isEmpty()) {
                        return Mono.error(new LlmRequestFailedException());
                    }

                    return Mono.just(response);
                })

                .switchIfEmpty(Mono.error(new LlmRequestFailedException()))

                .onErrorMap(Exception.class, e -> {
                    log.error("OpenRouter 요청 실패", e);
                    return new LlmRequestFailedException();
                });
    }
}