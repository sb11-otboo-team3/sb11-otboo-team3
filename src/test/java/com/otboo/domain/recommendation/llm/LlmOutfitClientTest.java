package com.otboo.domain.recommendation.llm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.recommendation.llm.dto.OpenRouterMessage;
import com.otboo.domain.recommendation.llm.exception.LlmRequestFailedException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class LlmOutfitClientTest {

    @Test
    @DisplayName("OpenRouter 요청이 전체 timeout을 초과하면 LlmRequestFailedException으로 변환한다")
    void requestCompletionTimeout() {
        // given
        WebClient webClient = WebClient.builder()
                .baseUrl("https://openrouter.test")
                .exchangeFunction(request -> Mono.never())
                .build();

        LlmOutfitClient client = new LlmOutfitClient(
                webClient,
                "test-api-key",
                "test-model",
                Duration.ofMillis(50)
        );

        // when & then
        assertThatThrownBy(() ->
                client.requestCompletion(
                                List.of(OpenRouterMessage.user("test"))
                        )
                        .block()
        )
                .isInstanceOf(LlmRequestFailedException.class);
    }

    @Test
    @DisplayName("OpenRouter 응답의 choices가 null이면 LlmRequestFailedException으로 처리한다")
    void requestCompletionWithNullChoices() {
        // given
        WebClient webClient = webClientReturning("""
                {"choices": null}
                """);

        LlmOutfitClient client = new LlmOutfitClient(
                webClient,
                "test-api-key",
                "test-model",
                Duration.ofSeconds(1)
        );

        // when & then
        assertThatThrownBy(() ->
                client.requestCompletion(
                                List.of(OpenRouterMessage.user("test"))
                        )
                        .block()
        )
                .isInstanceOf(LlmRequestFailedException.class);
    }

    @Test
    @DisplayName("OpenRouter 응답의 choices가 비어 있으면 LlmRequestFailedException으로 처리한다")
    void requestCompletionWithEmptyChoices() {
        // given
        WebClient webClient = webClientReturning("""
                {"choices": []}
                """);

        LlmOutfitClient client = new LlmOutfitClient(
                webClient,
                "test-api-key",
                "test-model",
                Duration.ofSeconds(1)
        );

        // when & then
        assertThatThrownBy(() ->
                client.requestCompletion(
                                List.of(OpenRouterMessage.user("test"))
                        )
                        .block()
        )
                .isInstanceOf(LlmRequestFailedException.class);
    }

    private WebClient webClientReturning(String body) {
        return WebClient.builder()
                .baseUrl("https://openrouter.test")
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .body(body)
                                .build()
                ))
                .build();
    }
}
