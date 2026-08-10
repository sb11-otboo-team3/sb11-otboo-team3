package com.otboo.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.Grid;
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
    );
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
    );
    entityManager.flush();
    entityManager.clear();

    Instant secondForecastedAt = Instant.parse("2026-07-30T05:00:00Z");

    // when: 나중 배치가 같은 시간대를 CLEAR/23.0으로 다시 예측
    Weather second = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), secondForecastedAt, forecastAt,
        "CLEAR", "NONE", 0.0, 20.0, 55.0, null, 23.0, null, null, null, 2.3
    );
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
}
