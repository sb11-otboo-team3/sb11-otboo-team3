package com.otboo.domain.clothes.extraction.client;

import com.otboo.domain.clothes.extraction.exception.ProductPageFetchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
public class ProductPageClient {

    private final WebClient webClient;

    public ProductPageClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> fetch(URI uri) {
        log.debug("상품 페이지 요청 시작: {}", uri);

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorMap(Exception.class, e -> {
                    log.error("상품 페이지 요청 실패: {}", uri, e);
                    return new ProductPageFetchException(uri.toString());
                });
    }
}
