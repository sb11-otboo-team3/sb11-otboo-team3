package com.otboo.domain.feed.core.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchFeedSearchService implements FeedSearchService {

  private static final String INDEX_NAME = "feeds";

  private final RestHighLevelClient searchClient;
  private final ObjectMapper objectMapper;
  private final FeedRepository feedRepository;

  @Override
  public void index(Feed feed) {
    WeatherSummaryDto weatherSummary = objectMapper.convertValue(feed.getWeatherSnapshot(), WeatherSummaryDto.class);

    FeedSearchDocument document = FeedSearchDocument.from(feed, weatherSummary);

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
  public void indexById(UUID feedId) {
    feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .ifPresentOrElse(
            this::index,
            () -> delete(feedId)
        );
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
    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
        .query(feedQuery(
            keywordLike,
            skyStatusEqual,
            precipitationTypeEqual,
            authorIdEqual
        ))
        .size(limit + 1)
        .trackTotalHits(true);

    sourceBuilder.sort(feedSort(sortBy, sortDirection));
    sourceBuilder.sort(idSort(sortDirection));

    Object[] searchAfter = searchAfterValues(cursor, idAfter, sortBy);

    if (searchAfter != null) {
      sourceBuilder.searchAfter(searchAfter);
    }

    SearchRequest request = new SearchRequest(INDEX_NAME)
        .source(sourceBuilder);

    SearchResponse response;
    try {
      response = searchClient.search(request, RequestOptions.DEFAULT);
    } catch (IOException | ElasticsearchException exception) {
      log.warn(
          "피드 Elasticsearch 검색 실패 - DB 검색 fallback 필요, keywordLike={}",
          keywordLike,
          exception
      );
      throw new IllegalStateException("피드 Elasticsearch 검색 실패", exception);
    }

    SearchHit[] hits = response.getHits().getHits();
    boolean hasNext = hits.length > limit;

    List<SearchHit> pageHits = Arrays.stream(hits)
        .limit(limit)
        .toList();

    List<UUID> feedIds = pageHits.stream()
        .map(this::extractFeedId)
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext && !pageHits.isEmpty()) {
      FeedSearchDocument lastDocument = toDocument(pageHits.get(pageHits.size() - 1));

      if (sortBy == SortBy.likeCount) {
        nextCursor = String.valueOf(lastDocument.likeCount());
      } else {
        nextCursor = lastDocument.createdAt().toString();
      }

      nextIdAfter = lastDocument.id();
    }

    return new FeedSearchResult(
        feedIds,
        nextCursor,
        nextIdAfter,
        hasNext,
        response.getHits().getTotalHits().value
    );
  }

  private BoolQueryBuilder feedQuery(
      String keywordLike,
      SkyStatus skyStatusEqual,
      PrecipitationType precipitationTypeEqual,
      UUID authorIdEqual
  ) {
    BoolQueryBuilder query = QueryBuilders.boolQuery();

    if (keywordLike == null || keywordLike.isBlank()) {
      query.must(QueryBuilders.matchAllQuery());
    } else {
      query.must(QueryBuilders.matchQuery("content", keywordLike.trim()));
    }

    if (skyStatusEqual != null) {
      query.filter(QueryBuilders.termQuery("skyStatus", skyStatusEqual.name()));
    }

    if (precipitationTypeEqual != null) {
      query.filter(QueryBuilders.termQuery("precipitationType", precipitationTypeEqual.name()));
    }

    if (authorIdEqual != null) {
      query.filter(QueryBuilders.termQuery("authorId", authorIdEqual.toString()));
    }

    return query;
  }

  private FieldSortBuilder feedSort(SortBy sortBy, SortDirection sortDirection) {
    SortOrder order = toSortOrder(sortDirection);

    if (sortBy == SortBy.likeCount) {
      return SortBuilders.fieldSort("likeCount")
          .order(order);
    }

    return SortBuilders.fieldSort("createdAt")
        .order(order);
  }

  private FieldSortBuilder idSort(SortDirection sortDirection) {
    return SortBuilders.fieldSort("id")
        .order(toSortOrder(sortDirection));
  }

  private SortOrder toSortOrder(SortDirection sortDirection) {
    if (sortDirection == SortDirection.ASCENDING) {
      return SortOrder.ASC;
    }

    return SortOrder.DESC;
  }

  private Object[] searchAfterValues(String cursor, UUID idAfter, SortBy sortBy) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return null;
    }

    if (sortBy == SortBy.likeCount) {
      return new Object[] {
          Long.parseLong(cursor),
          idAfter.toString()
      };
    }

    return new Object[] {
        cursor,
        idAfter.toString()
    };
  }

  private UUID extractFeedId(SearchHit hit) {
    return toDocument(hit).id();
  }

  private FeedSearchDocument toDocument(SearchHit hit) {
    return objectMapper.convertValue(
        hit.getSourceAsMap(),
        FeedSearchDocument.class
    );
  }
}