package com.otboo.domain.clothes.extraction.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.clothes.extraction.exception.ProductPageFetchException;
import java.io.IOException;
import java.net.URI;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class ProductPageClientTest {

    private MockWebServer mockWebServer;
    private ProductPageClient productPageClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        productPageClient = new ProductPageClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("정상 응답이면 HTML 본문을 그대로 반환한다")
    void returnsHtmlBody() {
        //given
        String html = "<html><body>상품 페이지</body></html>";
        mockWebServer.enqueue(new MockResponse()
                .setBody(html)
                .addHeader("Content-Type", MediaType.TEXT_HTML_VALUE));

        //when
        String result = productPageClient.fetch(URI.create(mockWebServer.url("/products/1").toString())).block();

        //then
        assertThat(result).isEqualTo(html);
    }

    @Test
    @DisplayName("상품 페이지 요청이 실패하면 ProductPageFetchException을 던진다")
    void throwsProductPageFetchExceptionWhenRequestFails() {
        //given
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        //when & then
        assertThatThrownBy(() ->
                productPageClient.fetch(URI.create(mockWebServer.url("/products/1").toString())).block())
                .isInstanceOf(ProductPageFetchException.class);
    }
}
