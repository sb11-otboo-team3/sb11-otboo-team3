package com.otboo.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * upsert()/updateDailyTemperatureRange()는 직접 작성한 쿼리(네이티브 SQL/JPQL)라, Mockito만으로는
 * SQL 자체가 맞는지 확인할 수 없다. 특히 upsert()의 INSERT ... ON CONFLICT ... RETURNING은
 * PostgreSQL 전용 문법이라 - application-test.yaml의 H2(설령 PostgreSQL 호환 모드라도)에서는
 * 파싱조차 안 된다(ON CONFLICT 자리에서 문법 오류) - 실제 PostgreSQL 컨테이너 위에서만 검증할 수 있다.
 * FlywayMigrationIntegrationTest와 같은 패턴(@Testcontainers + 실 PostgreSQL + Flyway 마이그레이션
 * 그대로 실행)을 따르되, 이 클래스만 이렇게 돌고 나머지 테스트는 여전히 H2를 그대로 쓴다.
 */
@Testcontainers
@EntityScan(basePackages = "com.otboo.domain")
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.batch.jdbc.initialize-schema=never"
})
@Transactional // 테스트 메서드마다 롤백해서, 같은 grid(x=60,y=127) 좌표를 여러 테스트가 재사용해도 충돌 안 나게 함
class WeatherRepositoryTest {

  @Autowired
  private WeatherRepository weatherRepository;

  @Autowired
  private EntityManager entityManager;

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  private Grid persistGrid(int x, int y) {
    Grid grid = Grid.builder().x(x).y(y).build();
    entityManager.persist(grid);
    entityManager.flush();
    return grid;
  }

  // 피드가 이 weather를 들고 있는 상황을 재현하기 위한 최소 feed row. author_id는 nullable이라
  // User를 따로 안 만들어도 된다 - deleteBatchOlderThan 테스트 전용 헬퍼.
  private void persistFeedReferencing(UUID weatherId) {
    entityManager.createNativeQuery(
            "INSERT INTO feeds (id, weather_id, weather_snapshot, content) "
                + "VALUES (:id, :weatherId, '{}'::jsonb, 'test')")
        .setParameter("id", UUID.randomUUID())
        .setParameter("weatherId", weatherId)
        .executeUpdate();
  }

  @Test
  @DisplayName("같은 (grid, forecastAt)에 row가 없으면 새로 만든다")
  void upsertInsertsWhenNoExistingRow() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant forecastedAt = Instant.parse("2026-07-30T05:00:00Z");
    Instant forecastAt = Instant.parse("2026-07-30T09:00:00Z");

    // when
    Weather result = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), forecastedAt, forecastAt,
        "CLEAR", "NONE", 0.0, 20.0, 55.0, null, 23.0, null, null, null, 2.3
    ).orElseThrow();
    entityManager.flush();
    entityManager.clear();

    // then
    assertThat(result.getId()).isNotNull();
    assertThat(result.getCreatedAt()).isNotNull();
    assertThat(result.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(result.getTemperatureCurrent()).isEqualTo(23.0);
    assertThat(weatherRepository.findByGridAndForecastAt(grid, forecastAt)).isPresent();
  }

  @Test
  @DisplayName("같은 (grid, forecastAt)에 이미 row가 있으면 id/createdAt은 유지한 채 나머지 값만 덮어쓴다")
  void upsertUpdatesExistingRowWhileKeepingIdAndCreatedAt() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant forecastAt = Instant.parse("2026-07-30T09:00:00Z");
    Instant firstForecastedAt = Instant.parse("2026-07-30T02:00:00Z");
    Weather first = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), firstForecastedAt, forecastAt,
        "CLOUDY", "NONE", 0.0, 10.0, 50.0, null, 20.0, null, null, null, 1.5
    ).orElseThrow();
    entityManager.flush();
    entityManager.clear();

    Instant secondForecastedAt = Instant.parse("2026-07-30T05:00:00Z");

    // when: 나중 배치(더 최신 발표)가 같은 시간대를 CLEAR/23.0으로 다시 예측
    Weather second = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), secondForecastedAt, forecastAt,
        "CLEAR", "NONE", 0.0, 20.0, 55.0, null, 23.0, null, null, null, 2.3
    ).orElseThrow();
    entityManager.flush();
    entityManager.clear();

    // then: id/createdAt은 첫 저장 그대로, 나머지 값은 두 번째 값으로 덮어써짐
    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
    assertThat(second.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(second.getTemperatureCurrent()).isEqualTo(23.0);
    assertThat(second.getForecastedAt()).isEqualTo(secondForecastedAt);

    List<Weather> all = weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, forecastAt, forecastAt.plusSeconds(1));
    assertThat(all).hasSize(1); // 새 row가 또 생긴 게 아니라 하나만 남아있음
  }

  @Test
  @DisplayName("updateDailyTemperatureRange는 그 날짜 범위에 속한 모든 row의 min/max를 통일해서 채운다")
  void updateDailyTemperatureRangeUnifiesAllRowsInDateRange() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant dayStart = Instant.parse("2026-07-30T00:00:00Z");
    weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), dayStart, dayStart.plusSeconds(3600 * 6),
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 15.0, null, null, null, 2.0
    );
    weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), dayStart, dayStart.plusSeconds(3600 * 15),
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 25.0, null, null, null, 2.0
    );
    entityManager.flush();
    entityManager.clear();

    Instant dayEnd = dayStart.plusSeconds(3600 * 24);

    // when
    weatherRepository.updateDailyTemperatureRange(grid, 15.0, 25.0, dayStart, dayEnd);
    entityManager.flush();
    entityManager.clear();

    // then
    List<Weather> all = weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, dayStart, dayEnd);
    assertThat(all).hasSize(2);
    assertThat(all).allSatisfy(w -> {
      assertThat(w.getTemperatureMin()).isEqualTo(15.0);
      assertThat(w.getTemperatureMax()).isEqualTo(25.0);
    });
  }

  @Test
  @DisplayName("공식 TMN/TMX가 있으면 그 값으로 resolvedMin/resolvedMax를 계산한다")
  void findDailyTemperatureRangeUsesOfficialValuesWhenPresent() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant dayStart = Instant.parse("2026-07-30T00:00:00Z");
    weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), dayStart, dayStart.plusSeconds(3600 * 6),
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 20.0, null, 18.0, null, 2.0
    ); // temperatureMin=18.0 (TMN)
    weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), dayStart, dayStart.plusSeconds(3600 * 15),
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 26.0, null, null, 27.0, 2.0
    ); // temperatureMax=27.0 (TMX)
    entityManager.flush();
    entityManager.clear();

    Instant dayEnd = dayStart.plusSeconds(3600 * 24);

    // when
    WeatherRepository.DailyTemperatureRangeProjection result =
        weatherRepository.findDailyTemperatureRange(grid.getId(), dayStart, dayEnd);

    // then
    assertThat(result.getResolvedMin()).isEqualTo(18.0);
    assertThat(result.getResolvedMax()).isEqualTo(27.0);
  }

  @Test
  @DisplayName("공식 TMN/TMX가 하나도 없으면 temperature_current로 직접 계산한다")
  void findDailyTemperatureRangeComputesFromCurrentTemperatureWhenNoOfficialValues() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant dayStart = Instant.parse("2026-07-30T00:00:00Z");
    weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), dayStart, dayStart.plusSeconds(3600 * 6),
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 15.0, null, null, null, 2.0
    );
    weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), dayStart, dayStart.plusSeconds(3600 * 15),
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 25.0, null, null, null, 2.0
    );
    entityManager.flush();
    entityManager.clear();

    Instant dayEnd = dayStart.plusSeconds(3600 * 24);

    // when
    WeatherRepository.DailyTemperatureRangeProjection result =
        weatherRepository.findDailyTemperatureRange(grid.getId(), dayStart, dayEnd);

    // then
    assertThat(result.getResolvedMin()).isEqualTo(15.0);
    assertThat(result.getResolvedMax()).isEqualTo(25.0);
  }

  @Test
  @DisplayName("그 날짜에 row가 하나도 없으면 resolvedMin/resolvedMax가 null이다")
  void findDailyTemperatureRangeReturnsNullWhenNoRowsExist() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant dayStart = Instant.parse("2026-07-30T00:00:00Z");
    Instant dayEnd = dayStart.plusSeconds(3600 * 24);

    // when
    WeatherRepository.DailyTemperatureRangeProjection result =
        weatherRepository.findDailyTemperatureRange(grid.getId(), dayStart, dayEnd);

    // then
    assertThat(result.getResolvedMin()).isNull();
    assertThat(result.getResolvedMax()).isNull();
  }

  @Test
  @DisplayName("이미 최신 발표로 갱신된 row에, 그보다 과거 발표가 뒤섞여서 나중에 도착해도 값이 되돌아가지 않는다")
  void upsertDoesNotRevertToOlderForecastedAt() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant forecastAt = Instant.parse("2026-07-30T09:00:00Z");
    Instant olderForecastedAt = Instant.parse("2026-07-30T02:00:00Z");
    Instant newerForecastedAt = Instant.parse("2026-07-30T05:00:00Z");

    // 최신 배치(05시 발표)가 먼저 저장됨
    Weather latest = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), newerForecastedAt, forecastAt,
        "CLEAR", "NONE", 0.0, 20.0, 55.0, null, 23.0, null, null, null, 2.3
    ).orElseThrow();
    entityManager.flush();
    entityManager.clear();

    // when: 순서가 뒤섞여서(재시도, 지연 등) 더 오래된 배치(02시 발표)가 나중에 도착
    Optional<Weather> result = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), olderForecastedAt, forecastAt,
        "CLOUDY", "NONE", 0.0, 10.0, 50.0, null, 20.0, null, null, null, 1.5
    );
    entityManager.flush();
    entityManager.clear();

    // then: 갱신은 스킵되어 빈 값이 리턴되고, DB엔 여전히 최신(05시) 값이 그대로 남아있음
    assertThat(result).isEmpty();
    Weather stillLatest = weatherRepository.findByGridAndForecastAt(grid, forecastAt).orElseThrow();
    assertThat(stillLatest.getId()).isEqualTo(latest.getId());
    assertThat(stillLatest.getForecastedAt()).isEqualTo(newerForecastedAt);
    assertThat(stillLatest.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(stillLatest.getTemperatureCurrent()).isEqualTo(23.0);
  }

  @Test
  @DisplayName("같은 forecastedAt이 다시 오면(재시도 등) 값 갱신은 허용된다")
  void upsertAllowsUpdateWhenForecastedAtIsEqual() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant forecastAt = Instant.parse("2026-07-30T09:00:00Z");
    Instant forecastedAt = Instant.parse("2026-07-30T05:00:00Z");
    weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), forecastedAt, forecastAt,
        "CLOUDY", "NONE", 0.0, 10.0, 50.0, null, 20.0, null, null, null, 1.5
    );
    entityManager.flush();
    entityManager.clear();

    // when: 같은 forecastedAt으로 다시 옴(예: 동시성 충돌 후 재시도)
    Optional<Weather> result = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), forecastedAt, forecastAt,
        "CLEAR", "NONE", 0.0, 20.0, 55.0, null, 23.0, null, null, null, 2.3
    );
    entityManager.flush();
    entityManager.clear();

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(result.get().getTemperatureCurrent()).isEqualTo(23.0);
  }

  @Test
  @DisplayName("forecastAt이 cutoff보다 과거고 피드도 참조하지 않으면 삭제된다")
  void deleteBatchOlderThanDeletesOldUnreferencedRows() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant forecastAt = Instant.parse("2026-08-01T00:00:00Z");
    Weather old = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), forecastAt, forecastAt,
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 20.0, null, null, null, 2.0
    ).orElseThrow();
    entityManager.flush();
    entityManager.clear();

    Instant cutoff = Instant.parse("2026-08-05T00:00:00Z");

    // when
    int deleted = weatherRepository.deleteBatchOlderThan(cutoff, 100);
    entityManager.flush();
    entityManager.clear();

    // then
    assertThat(deleted).isEqualTo(1);
    assertThat(weatherRepository.findById(old.getId())).isEmpty();
  }

  @Test
  @DisplayName("forecastAt이 cutoff 이후면(최근 데이터) 삭제되지 않는다")
  void deleteBatchOlderThanKeepsRecentRows() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant forecastAt = Instant.parse("2026-08-09T00:00:00Z");
    Weather recent = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), forecastAt, forecastAt,
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 20.0, null, null, null, 2.0
    ).orElseThrow();
    entityManager.flush();
    entityManager.clear();

    Instant cutoff = Instant.parse("2026-08-05T00:00:00Z");

    // when
    int deleted = weatherRepository.deleteBatchOlderThan(cutoff, 100);
    entityManager.flush();
    entityManager.clear();

    // then
    assertThat(deleted).isEqualTo(0);
    assertThat(weatherRepository.findById(recent.getId())).isPresent();
  }

  @Test
  @DisplayName("오래된 데이터라도 피드가 들고 있는 weather는 삭제되지 않는다")
  void deleteBatchOlderThanKeepsRowsReferencedByFeed() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant forecastAt = Instant.parse("2026-08-01T00:00:00Z");
    Weather heldByFeed = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), forecastAt, forecastAt,
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 20.0, null, null, null, 2.0
    ).orElseThrow();
    persistFeedReferencing(heldByFeed.getId());
    entityManager.flush();
    entityManager.clear();

    Instant cutoff = Instant.parse("2026-08-05T00:00:00Z");

    // when
    int deleted = weatherRepository.deleteBatchOlderThan(cutoff, 100);
    entityManager.flush();
    entityManager.clear();

    // then
    assertThat(deleted).isEqualTo(0);
    assertThat(weatherRepository.findById(heldByFeed.getId())).isPresent();
  }

  @Test
  @DisplayName("조건에 맞는 행이 batchSize보다 많으면 batchSize개까지만 지운다")
  void deleteBatchOlderThanRespectsBatchSizeLimit() {
    // given: cutoff보다 과거인 서로 다른 시간대 3개
    Grid grid = persistGrid(60, 127);
    Instant base = Instant.parse("2026-08-01T00:00:00Z");
    for (int i = 0; i < 3; i++) {
      Instant forecastAt = base.plusSeconds(3600L * i);
      weatherRepository.upsert(
          UUID.randomUUID(), grid.getId(), forecastAt, forecastAt,
          "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 20.0, null, null, null, 2.0
      );
    }
    entityManager.flush();
    entityManager.clear();

    Instant cutoff = Instant.parse("2026-08-05T00:00:00Z");

    // when
    int deleted = weatherRepository.deleteBatchOlderThan(cutoff, 2);
    entityManager.flush();
    entityManager.clear();

    // then: 2개만 지워지고 1개는 다음 호출을 위해 남아있음
    assertThat(deleted).isEqualTo(2);
    List<Weather> remaining = weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, base, base.plusSeconds(3600L * 3));
    assertThat(remaining).hasSize(1);
  }

  @Test
  @DisplayName("조건에 맞는 행이 하나도 없으면 0을 리턴한다(CONTINUABLE 루프 종료 조건)")
  void deleteBatchOlderThanReturnsZeroWhenNothingMatches() {
    // given: 삭제 대상이 될 데이터를 아예 안 만듦
    Instant cutoff = Instant.parse("2026-08-05T00:00:00Z");

    // when
    int deleted = weatherRepository.deleteBatchOlderThan(cutoff, 100);

    // then
    assertThat(deleted).isEqualTo(0);
  }
}
