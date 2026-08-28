package com.otboo.domain.feed.core.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * feedCursorCondition()을 OR 조합에서 (정렬필드, id) row-value 비교로 바꾼 수정(성능테스트 리포트
 * 4.1절)이 실제로 정렬/타이브레이크를 종전과 동일하게 수행하는지 검증한다. WeatherRepositoryTest와
 * 같은 이유로 H2가 아닌 실제 PostgreSQL 컨테이너 위에서 돈다 - feeds.weather_snapshot이 jsonb
 * 컬럼이라 H2(PostgreSQL 호환 모드 포함)에서는 테이블 생성 자체가 안 된다.
 */
@Testcontainers
@EntityScan(basePackages = "com.otboo.domain")
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.batch.jdbc.initialize-schema=never"
})
@Transactional
class FeedRepositoryImplTest {

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Test
  @DisplayName("createdAt 내림차순 커서로 다음 페이지를 가져온다")
  void createdAtDescCursorReturnsNextPage() {
    //given
    User author = persistAuthor();
    Weather weather = persistWeather();

    Feed first = persistFeed(author, weather, "1번");
    Feed second = persistFeed(author, weather, "2번");
    Feed third = persistFeed(author, weather, "3번");
    entityManager.flush();

    Instant base = Instant.now();
    updateCreatedAt(first.getId(), base);
    updateCreatedAt(second.getId(), base.plusSeconds(1));
    updateCreatedAt(third.getId(), base.plusSeconds(2));
    entityManager.clear();

    //when - 커서 없이 첫 페이지(limit 2)
    List<Feed> firstPage = feedRepository.findFeeds(
        null, null, 2, SortBy.createdAt, SortDirection.DESCENDING, null, null, null, null
    );

    //then
    assertThat(firstPage).extracting(Feed::getId)
        .containsExactly(third.getId(), second.getId());

    //when - 마지막 항목을 커서로 다음 페이지 조회
    Feed cursorItem = firstPage.get(1);
    List<Feed> secondPage = feedRepository.findFeeds(
        cursorItem.getCreatedAt().toString(), cursorItem.getId(), 2,
        SortBy.createdAt, SortDirection.DESCENDING, null, null, null, null
    );

    //then
    assertThat(secondPage).extracting(Feed::getId)
        .containsExactly(first.getId());
  }

  @Test
  @DisplayName("createdAt이 동일해도 id로 타이브레이크해서 중복·누락 없이 페이지네이션한다")
  void tieBreaksByIdWhenCreatedAtIsEqual() {
    //given
    User author = persistAuthor();
    Weather weather = persistWeather();

    Feed a = persistFeed(author, weather, "A");
    Feed b = persistFeed(author, weather, "B");
    entityManager.flush();

    Instant sameInstant = Instant.now();
    updateCreatedAt(a.getId(), sameInstant);
    updateCreatedAt(b.getId(), sameInstant);
    entityManager.clear();

    // DB(UUID 컬럼)의 실제 정렬 순서를 기준으로 삼는다 (Java UUID.compareTo()와 DB 정렬이 다를 수 있음).
    List<Feed> all = feedRepository.findFeeds(
        null, null, 2, SortBy.createdAt, SortDirection.DESCENDING, null, null, null, null
    );
    assertThat(all).hasSize(2);
    Feed firstOfAll = all.get(0);
    UUID expectedSecondId = all.get(1).getId();

    //when - 첫 페이지(limit 1)
    List<Feed> firstPage = feedRepository.findFeeds(
        null, null, 1, SortBy.createdAt, SortDirection.DESCENDING, null, null, null, null
    );

    //then
    assertThat(firstPage).extracting(Feed::getId)
        .containsExactly(firstOfAll.getId());

    //when - 커서로 다음 페이지 조회
    List<Feed> secondPage = feedRepository.findFeeds(
        firstOfAll.getCreatedAt().toString(), firstOfAll.getId(), 1,
        SortBy.createdAt, SortDirection.DESCENDING, null, null, null, null
    );

    //then - 남은 항목이 중복·누락 없이 나온다
    assertThat(secondPage).extracting(Feed::getId)
        .containsExactly(expectedSecondId);
  }

  @Test
  @DisplayName("likeCount 오름차순 커서로 다음 페이지를 가져온다")
  void likeCountAscCursorReturnsNextPage() {
    //given
    User author = persistAuthor();
    Weather weather = persistWeather();

    Feed low = persistFeed(author, weather, "낮음");
    Feed mid = persistFeed(author, weather, "중간");
    Feed high = persistFeed(author, weather, "높음");
    entityManager.flush();

    updateLikeCount(low.getId(), 1L);
    updateLikeCount(mid.getId(), 5L);
    updateLikeCount(high.getId(), 10L);
    entityManager.clear();

    //when - 커서 없이 첫 페이지(limit 2, 오름차순)
    List<Feed> firstPage = feedRepository.findFeeds(
        null, null, 2, SortBy.likeCount, SortDirection.ASCENDING, null, null, null, null
    );

    //then
    assertThat(firstPage).extracting(Feed::getId)
        .containsExactly(low.getId(), mid.getId());

    //when - 마지막 항목을 커서로 다음 페이지 조회
    Feed cursorItem = firstPage.get(1);
    List<Feed> secondPage = feedRepository.findFeeds(
        String.valueOf(cursorItem.getLikeCount()), cursorItem.getId(), 2,
        SortBy.likeCount, SortDirection.ASCENDING, null, null, null, null
    );

    //then
    assertThat(secondPage).extracting(Feed::getId)
        .containsExactly(high.getId());
  }

  @Test
  @DisplayName("createdAt 오름차순 커서로 다음 페이지를 가져온다")
  void createdAtAscCursorReturnsNextPage() {
    //given
    User author = persistAuthor();
    Weather weather = persistWeather();

    Feed first = persistFeed(author, weather, "1번");
    Feed second = persistFeed(author, weather, "2번");
    Feed third = persistFeed(author, weather, "3번");
    entityManager.flush();

    Instant base = Instant.now();
    updateCreatedAt(first.getId(), base);
    updateCreatedAt(second.getId(), base.plusSeconds(1));
    updateCreatedAt(third.getId(), base.plusSeconds(2));
    entityManager.clear();

    //when - 커서 없이 첫 페이지(limit 2, 오름차순 -> 오래된 것부터)
    List<Feed> firstPage = feedRepository.findFeeds(
        null, null, 2, SortBy.createdAt, SortDirection.ASCENDING, null, null, null, null
    );

    //then
    assertThat(firstPage).extracting(Feed::getId)
        .containsExactly(first.getId(), second.getId());

    //when - 마지막 항목을 커서로 다음 페이지 조회
    Feed cursorItem = firstPage.get(1);
    List<Feed> secondPage = feedRepository.findFeeds(
        cursorItem.getCreatedAt().toString(), cursorItem.getId(), 2,
        SortBy.createdAt, SortDirection.ASCENDING, null, null, null, null
    );

    //then
    assertThat(secondPage).extracting(Feed::getId)
        .containsExactly(third.getId());
  }

  @Test
  @DisplayName("likeCount 내림차순 커서로 다음 페이지를 가져온다")
  void likeCountDescCursorReturnsNextPage() {
    //given
    User author = persistAuthor();
    Weather weather = persistWeather();

    Feed low = persistFeed(author, weather, "낮음");
    Feed mid = persistFeed(author, weather, "중간");
    Feed high = persistFeed(author, weather, "높음");
    entityManager.flush();

    updateLikeCount(low.getId(), 1L);
    updateLikeCount(mid.getId(), 5L);
    updateLikeCount(high.getId(), 10L);
    entityManager.clear();

    //when - 커서 없이 첫 페이지(limit 2, 내림차순 -> 좋아요 많은 것부터)
    List<Feed> firstPage = feedRepository.findFeeds(
        null, null, 2, SortBy.likeCount, SortDirection.DESCENDING, null, null, null, null
    );

    //then
    assertThat(firstPage).extracting(Feed::getId)
        .containsExactly(high.getId(), mid.getId());

    //when - 마지막 항목을 커서로 다음 페이지 조회
    Feed cursorItem = firstPage.get(1);
    List<Feed> secondPage = feedRepository.findFeeds(
        String.valueOf(cursorItem.getLikeCount()), cursorItem.getId(), 2,
        SortBy.likeCount, SortDirection.DESCENDING, null, null, null, null
    );

    //then
    assertThat(secondPage).extracting(Feed::getId)
        .containsExactly(low.getId());
  }

  private User persistAuthor() {
    return userRepository.save(
        User.create("feed-cursor-" + UUID.randomUUID() + "@test.com", "tester", "hash"));
  }

  private Weather persistWeather() {
    Grid grid = Grid.builder().x(60).y(127).build();
    entityManager.persist(grid);

    Weather weather = Weather.builder()
        .grid(grid)
        .forecastedAt(Instant.now())
        .forecastAt(Instant.now())
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .build();
    entityManager.persist(weather);
    return weather;
  }

  private Feed persistFeed(User author, Weather weather, String content) {
    return feedRepository.save(
        Feed.create(author, weather, JsonNodeFactory.instance.objectNode(), content));
  }

  private void updateCreatedAt(UUID feedId, Instant createdAt) {
    entityManager.createNativeQuery("update feeds set created_at = ?1 where id = ?2")
        .setParameter(1, createdAt)
        .setParameter(2, feedId)
        .executeUpdate();
  }

  private void updateLikeCount(UUID feedId, long likeCount) {
    entityManager.createNativeQuery("update feeds set like_count = ?1 where id = ?2")
        .setParameter(1, likeCount)
        .setParameter(2, feedId)
        .executeUpdate();
  }
}
