package com.otboo.domain.feed.core.search;

import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedSearchReindexService {

  private static final int BATCH_SIZE = 100;

  private final FeedRepository feedRepository;
  private final FeedSearchService feedSearchService;

  @Transactional(readOnly = true)
  public long reindexAll() {
    long indexedCount = 0;
    int page = 0;

    Slice<Feed> feedSlice;

    do {
      feedSlice = feedRepository.findByDeletedAtIsNull(
          PageRequest.of(
              page,
              BATCH_SIZE,
              Sort.by(Sort.Direction.ASC, "createdAt")
          )
      );

      for (Feed feed : feedSlice.getContent()) {
        feedSearchService.index(feed);
        indexedCount++;
      }

      page++;
    } while (feedSlice.hasNext());

    log.info("피드 Elasticsearch reindex 완료: indexedCount={}", indexedCount);

    return indexedCount;
  }
}