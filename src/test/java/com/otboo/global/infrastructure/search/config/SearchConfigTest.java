package com.otboo.global.infrastructure.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SearchConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(SearchConfig.class)
                    .withPropertyValues(
                            "app.search.endpoint=http://localhost:9200"
                    );

    @Test
    @DisplayName("검색 엔드포인트 설정을 바인딩하고 Elasticsearch Client Bean을 등록한다")
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
        });
    }

    @Test
    @DisplayName("검색 엔드포인트가 누락되면 애플리케이션 컨텍스트 생성에 실패한다")
    void failContextWhenSearchEndpointIsMissing() {
        // given
        ApplicationContextRunner invalidContextRunner =
                new ApplicationContextRunner()
                        .withUserConfiguration(SearchConfig.class);

        // when & then
        invalidContextRunner.run(context ->
                assertThat(context).hasFailed()
        );
    }
}