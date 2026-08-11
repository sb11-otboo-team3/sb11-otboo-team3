package com.otboo.domain.weather.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/**
 * weatherPrefetchJob을 실제로 실행해서 Reader(활성 격자 필터)/Processor(재시도)/Writer(저장)/skip 정책이
 * 전부 맞물려 돌아가는지 검증한다. WeatherRepositoryTest와 같은 이유로 Testcontainers 실 PostgreSQL 사용 -
 * WeatherPersister가 결국 WeatherRepository.upsert(네이티브 ON CONFLICT)까지 타므로 H2로는 안 됨.
 * 이 테스트 클래스는 @Transactional을 안 쓴다 - Job 안에서 청크마다 별도 트랜잭션으로 커밋되는 걸
 * 바깥에서 하나의 테스트 트랜잭션으로 감싸버리면 Spring Batch의 자체 트랜잭션 경계(잡 리포지토리 기록 등)와
 * 충돌한다(GridSaverIntegrationTest와 동일한 이유로 회피).
 */
@Testcontainers
@EntityScan(basePackages = "com.otboo.domain")
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.batch.jdbc.initialize-schema=never"
})
class WeatherPrefetchJobIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Autowired
  private JobLauncher jobLauncher;

  @Autowired
  @Qualifier("weatherPrefetchJob")
  private Job weatherPrefetchJob;

  @Autowired
  private GridRepository gridRepository;

  @Autowired
  private WeatherRepository weatherRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @MockitoBean
  private KmaWeatherClient kmaWeatherClient;

  @AfterEach
  void tearDown() {
    weatherRepository.deleteAll();
    gridRepository.deleteAll();
  }

  // gridRepository.saveAndFlush(...)는 리포지토리 프록시 자체가 트랜잭션을 걸어주므로 별도 트랜잭션
  // 없이 호출 가능(테스트 클래스 자체엔 @Transactional을 안 붙였음 - 배치의 자체 트랜잭션과 안 겹치게).
  private Grid persistGrid(int x, int y) {
    return gridRepository.saveAndFlush(Grid.builder().x(x).y(y).build());
  }

  // Grid.lastRequestedAt은 생성 시 Instant.now()로 고정돼서 빌더로 과거 값을 못 넣는다 - 비활성 격자를
  // 재현하려면 저장 후 직접 과거로 되돌려야 한다. EntityManager native 쿼리는 리포지토리 프록시와 달리
  // 자체 트랜잭션이 없어서 직접 짧은 트랜잭션으로 감싼다.
  private void backdateLastRequestedAt(Grid grid, Instant timestamp) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
        entityManager.createNativeQuery("UPDATE weather_grid SET last_requested_at = :ts WHERE id = :id")
            .setParameter("ts", timestamp)
            .setParameter("id", grid.getId())
            .executeUpdate());
    entityManager.clear();
  }

  private VilageFcstItem item() {
    return new VilageFcstItem(
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now().plusHours(2),
        SkyStatus.CLEAR, PrecipitationType.NONE,
        0.0, 20.0, 55.0, 23.0, null, null, 2.3
    );
  }

  private JobParameters uniqueJobParameters() {
    return new JobParametersBuilder()
        .addString("uuid", UUID.randomUUID().toString())
        .toJobParameters();
  }

  private StepExecution stepExecution(JobExecution jobExecution) {
    return jobExecution.getStepExecutions().stream()
        .filter(se -> se.getStepName().equals("weatherPrefetchStep"))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("활성 격자만 기상청 호출/저장되고, 비활성 격자는 건드리지 않는다")
  void onlyActiveGridsAreFetchedAndSaved() throws Exception {
    // given
    Grid activeGrid = persistGrid(60, 127);
    Grid inactiveGrid = persistGrid(61, 128);
    backdateLastRequestedAt(inactiveGrid, Instant.now().minus(4, ChronoUnit.DAYS)); // 활성 기준(3일)보다 과거

    given(kmaWeatherClient.getForecast(eq(activeGrid.getX()), eq(activeGrid.getY()), any(VilageFcstBaseTime.class)))
        .willReturn(Mono.just(List.of(item())));

    // when
    JobExecution jobExecution = jobLauncher.run(weatherPrefetchJob, uniqueJobParameters());

    // then
    assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    verify(kmaWeatherClient, never())
        .getForecast(eq(inactiveGrid.getX()), eq(inactiveGrid.getY()), any(VilageFcstBaseTime.class));
    assertThat(weatherRepository.findAll())
        .anySatisfy(w -> assertThat(w.getGrid().getId()).isEqualTo(activeGrid.getId()));
    assertThat(weatherRepository.findAll())
        .noneSatisfy(w -> assertThat(w.getGrid().getId()).isEqualTo(inactiveGrid.getId()));
  }

  @Test
  @DisplayName("재시도를 다 써도 계속 실패하면 그 격자만 skip되고, 나머지는 정상 처리되며 Job은 COMPLETED로 끝난다")
  void skipsGridAfterRetriesExhaustedButJobStillCompletes() throws Exception {
    // given
    Grid okGrid = persistGrid(60, 127);
    Grid failingGrid = persistGrid(62, 129);

    given(kmaWeatherClient.getForecast(eq(okGrid.getX()), eq(okGrid.getY()), any(VilageFcstBaseTime.class)))
        .willReturn(Mono.just(List.of(item())));
    given(kmaWeatherClient.getForecast(eq(failingGrid.getX()), eq(failingGrid.getY()), any(VilageFcstBaseTime.class)))
        .willReturn(Mono.error(new KmaApiException(failingGrid.getX(), failingGrid.getY(), null, null)));

    // when
    JobExecution jobExecution = jobLauncher.run(weatherPrefetchJob, uniqueJobParameters());

    // then: 격자 하나 실패로 Job 전체가 죽지 않는다
    assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(stepExecution(jobExecution).getProcessSkipCount()).isEqualTo(1);
    // retryLimit(3)만큼 재시도했는지
    verify(kmaWeatherClient, times(3))
        .getForecast(eq(failingGrid.getX()), eq(failingGrid.getY()), any(VilageFcstBaseTime.class));

    assertThat(weatherRepository.findAll())
        .anySatisfy(w -> assertThat(w.getGrid().getId()).isEqualTo(okGrid.getId()));
    assertThat(weatherRepository.findAll())
        .noneSatisfy(w -> assertThat(w.getGrid().getId()).isEqualTo(failingGrid.getId()));
  }

  @Test
  @DisplayName("재시도 중간에 성공하면(2번 실패 + 3번째 성공) skip되지 않고 정상 저장된다")
  void savesGridThatRecoversDuringRetry() throws Exception {
    // given
    Grid recoveringGrid = persistGrid(63, 130);
    given(kmaWeatherClient.getForecast(eq(recoveringGrid.getX()), eq(recoveringGrid.getY()), any(VilageFcstBaseTime.class)))
        .willReturn(Mono.error(new KmaApiException(recoveringGrid.getX(), recoveringGrid.getY(), null, null)))
        .willReturn(Mono.error(new KmaApiException(recoveringGrid.getX(), recoveringGrid.getY(), null, null)))
        .willReturn(Mono.just(List.of(item())));

    // when
    JobExecution jobExecution = jobLauncher.run(weatherPrefetchJob, uniqueJobParameters());

    // then
    assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(stepExecution(jobExecution).getProcessSkipCount()).isZero();
    verify(kmaWeatherClient, times(3))
        .getForecast(eq(recoveringGrid.getX()), eq(recoveringGrid.getY()), any(VilageFcstBaseTime.class));
    assertThat(weatherRepository.findAll())
        .anySatisfy(w -> assertThat(w.getGrid().getId()).isEqualTo(recoveringGrid.getId()));
  }
}
