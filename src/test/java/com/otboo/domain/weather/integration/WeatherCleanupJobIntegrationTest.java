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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

  @Autowired
  private PlatformTransactionManager transactionManager;

  @AfterEach
  void tearDown() {
    weatherRepository.deleteAll();
    gridRepository.deleteAll();
  }

  // gridRepository.save(...)는 리포지토리 프록시 자체가 트랜잭션을 걸어주므로 별도 트랜잭션 없이 호출 가능.
  private Grid persistGrid(int x, int y) {
    return gridRepository.saveAndFlush(Grid.builder().x(x).y(y).build());
  }

  // weatherRepository.upsert(...)도 마찬가지로 리포지토리 프록시가 자체 트랜잭션을 걸어준다.
  private Weather persistWeather(Grid grid, Instant forecastAt) {
    return weatherRepository.upsert(
        UUID.randomUUID(), grid.getId(), forecastAt, forecastAt,
        "CLEAR", "NONE", 0.0, 0.0, 45.0, null, 20.0, null, null, null, 2.0
    ).orElseThrow();
  }

  // 반면 이건 EntityManager로 직접 쏘는 native 쿼리라, 배치의 트랜잭션과 안 겹치는 별도의 짧은
  // 트랜잭션으로 직접 감싸줘야 한다(테스트 클래스 자체엔 @Transactional을 안 붙였으므로).
  private void persistFeedReferencing(UUID weatherId) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
        entityManager.createNativeQuery(
                "INSERT INTO feeds (id, weather_id, weather_snapshot, content) "
                    + "VALUES (:id, :weatherId, '{}'::jsonb, 'test')")
            .setParameter("id", UUID.randomUUID())
            .setParameter("weatherId", weatherId)
            .executeUpdate());
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
    // old와 forecastAt이 겹치면 upsert가 같은 (grid, forecastAt) 행으로 합쳐버려서(같은 id) 별개
    // 검증이 안 되므로 다른 시각을 씀 - 그래도 cutoff(3일) 이전인 건 동일.
    Weather oldButHeldByFeed = persistWeather(grid, now.minusSeconds(3600L * 24 * 9));
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
