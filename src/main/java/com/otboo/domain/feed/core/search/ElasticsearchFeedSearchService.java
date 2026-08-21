package com.otboo.domain.feed.core.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchFeedSearchService implements FeedSearchService {

  private static final String INDEX_NAME = "feeds";

  private final RestHighLevelClient searchClient;
  private final ObjectMapper objectMapper;

  @Override
  public void index(Feed feed) {
    // Feed Entity를 검색용 document로 변환
    FeedSearchDocument document = FeedSearchDocument.from(feed);

    String source;
    try {
      source = objectMapper.writeValueAsString(document);
    } catch (JsonProcessingException exception) {
      log.warn(
          "피드 검색 문서 직렬화 실패 - Elasticsearch 색인 생략, feedId={}",
          feed.getId(),
          exception
      );
      return;
    }

    IndexRequest request = new IndexRequest(INDEX_NAME)
        .id(feed.getId().toString())
        .source(source, XContentType.JSON);

    try {
      searchClient.index(request, RequestOptions.DEFAULT);
    } catch (IOException | ElasticsearchException exception) {
      log.warn(
          "피드 Elasticsearch 인덱스 실패 - DB 저장은 유지, feedId={}",
          feed.getId(),
          exception
      );
    }
  }

  @Override
  public void delete(UUID feedId) {
    DeleteRequest request = new DeleteRequest(INDEX_NAME)
        .id(feedId.toString());

    try {
      searchClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException | ElasticsearchException exception) {
      log.warn(
          "피드 Elasticsearch 문서 삭제 실패 - DB 삭제는 유지, feedId={}",
          feedId,
          exception
      );
    }
  }

  @Override
  public FeedSearchResult search(
      String cursor,
      UUID idAfter,
      int limit,
      SortBy sortBy,
      SortDirection sortDirection,
      String keywordLike,
      SkyStatus skyStatusEqual,
      PrecipitationType precipitationTypeEqual,
      UUID authorIdEqual
  ) {
    throw new UnsupportedOperationException("피드 Elasticsearch 검색은 아직 구현되지 않았습니다.");
  }
}