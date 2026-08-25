package com.otboo.global.infrastructure.search.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class SearchConfig {

    @Bean(destroyMethod = "close")
    public RestHighLevelClient searchClient(
            SearchProperties searchProperties
    ) {
        int connectTimeoutMillis =
                Math.toIntExact(searchProperties.connectTimeout().toMillis());

        int socketTimeoutMillis =
                Math.toIntExact(searchProperties.socketTimeout().toMillis());

        return new RestHighLevelClient(
                RestClient.builder(
                                HttpHost.create(searchProperties.endpoint())
                        )
                        .setRequestConfigCallback(requestConfigBuilder ->
                                requestConfigBuilder
                                        .setConnectTimeout(connectTimeoutMillis)
                                        .setSocketTimeout(socketTimeoutMillis)
                        )
        );
    }
}