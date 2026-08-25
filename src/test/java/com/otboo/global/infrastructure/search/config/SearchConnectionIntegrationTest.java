package com.otboo.global.infrastructure.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@EnabledIfEnvironmentVariable(
        named = "RUN_SEARCH_INTEGRATION_TEST",
        matches = "true"
)
class SearchConnectionIntegrationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(SearchConfig.class)
                    .withPropertyValues(
                            "app.search.endpoint=http://localhost:9200",
                            "app.search.connect-timeout=3s",
                            "app.search.socket-timeout=5s"
                    );

    @Test
    @DisplayName("Spring Elasticsearch Client가 로컬 Elasticsearch에 연결된다")
    void connectToLocalElasticsearch() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            RestHighLevelClient client =
                    context.getBean(RestHighLevelClient.class);

            try {
                assertThat(client.ping(RequestOptions.DEFAULT))
                        .isTrue();
            } catch (IOException e) {
                throw new AssertionError(
                        "로컬 Elasticsearch 연결에 실패했습니다.",
                        e
                );
            }
        });
    }
}