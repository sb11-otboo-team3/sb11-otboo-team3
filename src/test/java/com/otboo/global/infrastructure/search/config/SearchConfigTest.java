package com.otboo.global.infrastructure.search.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;

import java.time.Duration;
import java.io.IOException;
import java.net.InetSocketAddress;

import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.elasticsearch.client.RequestOptions;

class SearchConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(SearchConfig.class)
                    .withPropertyValues(
                            "app.search.endpoint=http://localhost:9200",
                            "app.search.connect-timeout=3s",
                            "app.search.socket-timeout=5s"
                    );

    @Test
    @DisplayName("검색 설정을 바인딩하고 Elasticsearch Client Bean을 등록한다")
    void bindSearchPropertiesAndRegisterClient() {

        // when & then
        contextRunner.run(context -> {

            assertThat(context).hasNotFailed();

            assertThat(context)
                    .hasSingleBean(SearchProperties.class);

            assertThat(context)
                    .hasSingleBean(RestHighLevelClient.class);

            SearchProperties properties =
                    context.getBean(SearchProperties.class);

            assertThat(properties.endpoint())
                    .isEqualTo("http://localhost:9200");

            assertThat(properties.connectTimeout())
                    .isEqualTo(Duration.ofSeconds(3));

            assertThat(properties.socketTimeout())
                    .isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    @DisplayName("검색 엔드포인트가 누락되면 애플리케이션 컨텍스트 생성에 실패한다")
    void failContextWhenSearchEndpointIsMissing() {

        // given
        ApplicationContextRunner invalidContextRunner =
                new ApplicationContextRunner()
                        .withUserConfiguration(SearchConfig.class)
                        .withPropertyValues(
                                "app.search.connect-timeout=3s",
                                "app.search.socket-timeout=5s"
                        );

        // when & then
        invalidContextRunner.run(context ->
                assertThat(context).hasFailed()
        );
    }

    @Test
    @DisplayName("설정한 socket timeout을 Elasticsearch Client 요청에 적용한다")
    void applySocketTimeoutToElasticsearchClient() throws Exception {

        // given
        HttpServer server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        server.createContext("/", exchange -> {
            try {
                Thread.sleep(1_000);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        server.start();

        int port = server.getAddress().getPort();

        ApplicationContextRunner timeoutContextRunner =
                new ApplicationContextRunner()
                        .withUserConfiguration(SearchConfig.class)
                        .withPropertyValues(
                                "app.search.endpoint=http://localhost:" + port,
                                "app.search.connect-timeout=200ms",
                                "app.search.socket-timeout=100ms"
                        );

        try {
            // when & then
            timeoutContextRunner.run(context -> {

                assertThat(context).hasNotFailed();

                RestHighLevelClient client =
                        context.getBean(RestHighLevelClient.class);

                assertThatThrownBy(() ->
                        client.ping(RequestOptions.DEFAULT)
                )
                        .isInstanceOf(IOException.class);
            });

        } finally {
            server.stop(0);
        }
    }
}