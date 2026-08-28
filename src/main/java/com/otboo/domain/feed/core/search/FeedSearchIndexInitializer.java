package com.otboo.domain.feed.core.search;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedSearchIndexInitializer implements ApplicationRunner {

  private static final String INDEX_NAME = "feeds";

  private final RestHighLevelClient searchClient;

  @Override
  public void run(ApplicationArguments args) {
    try {
      if (existsIndex()) {
        return;
      }

      createIndex();
    } catch (IOException | ElasticsearchException exception) {
      log.warn("피드 검색 인덱스 초기화 실패 - Elasticsearch 검색 기능이 제한될 수 있습니다.", exception);
    }
  }

  private boolean existsIndex() throws IOException {
    GetIndexRequest request = new GetIndexRequest(INDEX_NAME);
    return searchClient.indices().exists(request, RequestOptions.DEFAULT);
  }

  private void createIndex() throws IOException {
    CreateIndexRequest request = new CreateIndexRequest(INDEX_NAME);

    request.source("""
    {
      "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "index": {
          "max_ngram_diff": 19
        },
        "analysis": {
          "tokenizer": {
            "feed_content_ngram_tokenizer": {
              "type": "ngram",
              "min_gram": 1,
              "max_gram": 20,
              "token_chars": [
                "letter",
                "digit"
              ]
            }
          },
          "analyzer": {
            "feed_content_index_analyzer": {
              "type": "custom",
              "tokenizer": "feed_content_ngram_tokenizer",
              "filter": [
                "lowercase"
              ]
            },
            "feed_content_search_analyzer": {
              "type": "custom",
              "tokenizer": "standard",
              "filter": [
                "lowercase"
              ]
            }
          }
        }
      },
      "mappings": {
        "properties": {
          "id": {
            "type": "keyword"
          },
          "authorId": {
            "type": "keyword"
          },
          "content": {
            "type": "text",
            "analyzer": "feed_content_index_analyzer",
            "search_analyzer": "feed_content_search_analyzer"
          },
          "skyStatus": {
            "type": "keyword"
          },
          "precipitationType": {
            "type": "keyword"
          },
          "createdAt": {
            "type": "date"
          },
          "likeCount": {
            "type": "long"
          }
        }
      }
    }
    """, XContentType.JSON);

    searchClient.indices().create(request, RequestOptions.DEFAULT);

    log.info("피드 검색 인덱스 생성 완료: index={}", INDEX_NAME);
  }
}