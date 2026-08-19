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
        return new RestHighLevelClient(
                RestClient.builder(
                        HttpHost.create(searchProperties.endpoint())
                )
        );
    }
}