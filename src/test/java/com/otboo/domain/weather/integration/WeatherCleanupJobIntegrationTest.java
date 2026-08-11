package com.otboo.domain.weather.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.repository.WeatherRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * weatherCleanupJob을 실제로 실행해서 Job/Step 배선(반복 삭제 tasklet)이 도는지 확인한다. SQL 조건
 * 자체(cutoff/피드 제외/batchSize)는 이미 WeatherRepositoryTest가 촘촘히 검증하므로, 여긴 Job이 실제로
 * 실행되어 그 쿼리를 태우고 COMPLETED로 끝나는지 위주로 가볍게만 본다. @Transactional 안 쓰는 이유는
 * WeatherPrefetchJobIntegrationTest와 동일.
 */
@Testcontainers
@EntityScan(basePackages = "com.otboo.domain")
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.batch.jdbc.initialize-schema=never"
})
class WeatherCleanupJobIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Autowired
  private JobLauncher jobLauncher;

  @Autowired
  @Qualifier("weatherCleanupJob")
  private Job weatherCleanupJob;

  @Autowired
  private GridRepository gridRepository;

  @Autowired
  private WeatherRepository weatherRepository;

  @Autowired
  private EntityManager entityManager;

  @AfterEach
  void tearDown() {
    weatherRepository.deleteAll();
    gridRepository.deleteAll();
  }

  private Grid persistGrid(int x, int y) {
    Grid grid = Grid.builder().x(x).y(y).build();
    entityManager.persist(grid);
    entityManager.flush();
    return grid;
  }

  private Weather persistWeather(Grid grid, Instant forecastAt) {
    Weather weather = weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), forecastAt, forecastAt,
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 20.0, null, null, null, 2.0
    ).orElseThrow();
    entityManager.flush();
    return weather;
  }

  private void persistFeedReferencing(UUID weatherId) {
    entityManager.createNativeQuery(
            "INSERT INTO feeds (id, weather_id, weather_snapshot, content) "
                + "VALUES (:id, :weatherId, '{}'::jsonb, 'test')")
        .setParameter("id", UUID.randomUUID())
        .setParameter("weatherId", weatherId)
        .executeUpdate();
  }

  private JobParameters uniqueJobParameters() {
    return new JobParametersBuilder()
        .addString("uuid", UUID.randomUUID().toString())
        .toJobParameters();
  }

  @Test
  @DisplayName("Job을 실행하면 오래된 weather는 지워지고, 최근/피드가 참조하는 weather는 남는다")
  void deletesOldWeatherButKeepsRecentAndFeedReferenced() throws Exception {
    // given
    Grid grid = persistGrid(60, 127);
    Instant now = Instant.now();
    Weather old = persistWeather(grid, now.minusSeconds(3600L * 24 * 10)); // 10일 전 - 확실히 cutoff(3일) 이전
    Weather recent = persistWeather(grid, now.minusSeconds(3600)); // 1시간 전 - retention 기간 내
    Weather oldButHeldByFeed = persistWeather(grid, now.minusSeconds(3600L * 24 * 10));
    persistFeedReferencing(oldButHeldByFeed.getId());
    entityManager.clear();

    // when
    JobExecution jobExecution = jobLauncher.run(weatherCleanupJob, uniqueJobParameters());

    // then
    assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(weatherRepository.findById(old.getId())).isEmpty();
    assertThat(weatherRepository.findById(recent.getId())).isPresent();
    assertThat(weatherRepository.findById(oldButHeldByFeed.getId())).isPresent();
  }
}
