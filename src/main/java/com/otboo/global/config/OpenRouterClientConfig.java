package com.otboo.global.config;

import com.otboo.domain.clothes.llm.ClothesVisionTaggingClient;
import com.otboo.domain.recommendation.llm.LlmOutfitClient;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class OpenRouterClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public LlmOutfitClient llmOutfitClient(
            @Value("${openrouter.base-url}") String baseUrl,
            @Value("${openrouter.api-key}") String apiKey,
            @Value("${openrouter.model}") String model
    ) {
        return new LlmOutfitClient(buildWebClient(baseUrl), apiKey, model);
    }

    @Bean
    public ClothesVisionTaggingClient clothesVisionTaggingClient(
            @Value("${openrouter.base-url}") String baseUrl,
            @Value("${openrouter.api-key}") String apiKey,
            @Value("${openrouter.vision-model}") String visionModel
    ) {
        return new ClothesVisionTaggingClient(buildWebClient(baseUrl), apiKey, visionModel);
    }

    private WebClient buildWebClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .responseTimeout(READ_TIMEOUT);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
