package com.otboo.domain.clothes.extraction.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.clothes.extraction.exception.ProductPageFetchException;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

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

    @Test
    @DisplayName("응답이 지연되어 타임아웃이 발생하면 ProductPageFetchException을 던진다")
    void throwsProductPageFetchExceptionOnTimeout() {
        //given - 실제 운영 설정(5초)까지 기다리지 않도록, 이 테스트에서만 짧은 타임아웃(200ms)을 건 클라이언트를 사용한다.
        HttpClient shortTimeoutHttpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofMillis(200));
        WebClient shortTimeoutWebClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .clientConnector(new ReactorClientHttpConnector(shortTimeoutHttpClient))
                .build();
        ProductPageClient timeoutSensitiveClient = new ProductPageClient(shortTimeoutWebClient);

        mockWebServer.enqueue(new MockResponse()
                .setBody("<html></html>")
                .setBodyDelay(1, TimeUnit.SECONDS));

        //when & then
        assertThatThrownBy(() ->
                timeoutSensitiveClient.fetch(URI.create(mockWebServer.url("/products/1").toString())).block())
                .isInstanceOf(ProductPageFetchException.class);
    }
}
