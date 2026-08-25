package com.otboo.domain.weather.batch;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.util.ActiveGridFinder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.item.support.builder.SynchronizedItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

// 활성 격자(최근에 실제로 요청된 격자)의 날씨를 기상청 발표 직후 미리 조회해서 저장해둔다.
// Reader(활성 격자 목록) -> Processor(격자별 기상청 조회, GridForecastProcessor) ->
// Writer(저장 + 일 min/max 반영, GridForecastWriter) 구조의 chunk 지향 Step.
// 격자 하나가 기상청 호출에 실패해도(KmaApiException) 그 격자만 건너뛰고 나머지는 계속 진행한다(faultTolerant).
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WeatherPrefetchJobConfig {

  // 격자 하나 처리 자체가 무거운 작업(기상청 네트워크 호출 + 재시도)이라, 여러 개를 묶어서 커밋 오버헤드를
  // 줄이는 이점보다 격자 단위로 바로바로 병렬 처리하는 이점이 커서 청크 크기를 1로 잡는다.
  private static final int CHUNK_SIZE = 1;
  // 격자들을 동시에 처리할 스레드 수. 순차 처리였을 때 격자 수만큼 그대로 늘어나던 최악 소요시간
  // (재시도 다 실패 시 격자당 최대 ~30초)이 이 값만큼 나눠짐 - 활성 격자 500개 기준으로도
  // ShedLock lockAtMostFor(30분, WeatherPrefetchScheduler 참고) 안에 들어오게 계산한 값.
  // application.yaml의 HikariCP 풀 크기(15)와 세트로 맞춘 값이라 같이 조정할 것.
  private static final int CONCURRENCY = 10;
  private static final int RETRY_LIMIT = 3;
  private static final long BACKOFF_INITIAL_INTERVAL_MS = 200;
  private static final long BACKOFF_MAX_INTERVAL_MS = 2000;
  private static final double BACKOFF_MULTIPLIER = 2.0;

  private final ActiveGridFinder activeGridFinder;

  @Bean
  public Job weatherPrefetchJob(
      JobRepository jobRepository,
      Step weatherPrefetchStep,
      WeatherPrefetchMetricsListener weatherPrefetchMetricsListener
  ) {
    return new JobBuilder("weatherPrefetchJob", jobRepository)
        .listener(weatherPrefetchMetricsListener)
        .start(weatherPrefetchStep)
        .build();
  }

  // step-scope인 이유: 이 배치는 하루에 8번 도는데, 활성 격자 조회 기준 시각(threshold)이 매번 "지금"
  // 기준으로 새로 계산돼야 한다. 싱글턴 빈이면 애플리케이션 기동 시점의 threshold가 고정돼버린다.
  @Bean
  @StepScope
  public ListItemReader<Grid> activeGridReader() {
    List<Grid> activeGrids = activeGridFinder.findActiveGrids();
    log.info("날씨 프리패치 대상 활성 격자 수: {}", activeGrids.size());
    return new ListItemReader<>(activeGrids);
  }

  @Bean
  public TaskExecutor weatherPrefetchTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(CONCURRENCY);
    executor.setMaxPoolSize(CONCURRENCY);
    executor.setThreadNamePrefix("weather-prefetch-");
    executor.initialize();
    return executor;
  }

  @Bean
  public Step weatherPrefetchStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ListItemReader<Grid> activeGridReader,
      GridForecastProcessor gridForecastProcessor,
      GridForecastWriter gridForecastWriter,
      TaskExecutor weatherPrefetchTaskExecutor
  ) {
    // ListItemReader는 스레드 안전하지 않아서(내부 Iterator를 그대로 씀) 여러 스레드가 동시에 호출하면
    // 깨질 수 있다 - SynchronizedItemReader로 감싸 read() 호출만 직렬화한다. 읽기 자체는
    // 리스트에서 하나 꺼내는 것뿐이라 순식간이라 병목이 안 되고, 오래 걸리는 기상청 호출/저장은
    // 그대로 병렬로 돈다.
    ItemReader<Grid> synchronizedReader = new SynchronizedItemReaderBuilder<Grid>()
        .delegate(activeGridReader)
        .build();

    return new StepBuilder("weatherPrefetchStep", jobRepository)
        .<Grid, GridForecast>chunk(CHUNK_SIZE, transactionManager)
        .reader(synchronizedReader)
        .processor(gridForecastProcessor)
        .writer(gridForecastWriter)
        .taskExecutor(weatherPrefetchTaskExecutor)
        .throttleLimit(CONCURRENCY)
        .faultTolerant()
        // 기상청 호출이 일시적으로 삐끗한 걸 수도 있으니, 바로 포기하지 않고 지수 백오프로 몇 번 더
        // 찔러본다(200ms -> 400ms -> ... 최대 2초, 최대 3번 시도). 그래도 계속 실패하면 아래 skip으로 넘어감.
        .retry(KmaApiException.class)
        .retryLimit(RETRY_LIMIT)
        .backOffPolicy(retryBackOffPolicy())
        .skip(KmaApiException.class)
        // 격자 하나 실패로 배치 전체가 죽으면 안 되므로 사실상 무제한 허용 - 실패한 격자는 다음 발표
        // 시각(3시간 뒤)에 다시 시도된다.
        .skipLimit(Integer.MAX_VALUE)
        .listener(new SkipListener<Grid, GridForecast>() {
          @Override
          public void onSkipInProcess(Grid item, Throwable t) {
            // 이번 사이클 전체가 비어, 그 구간의 발표별 급변 diff도 같이 유실됨(WeatherPersister 참고).
            log.error("날씨 프리패치 - 격자 처리 스킵(재시도 {}번 다 실패), diff 유실 가능, grid=({},{})",
                RETRY_LIMIT, item.getX(), item.getY(), t);
          }
        })
        .build();
  }

  private BackOffPolicy retryBackOffPolicy() {
    ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
    policy.setInitialInterval(BACKOFF_INITIAL_INTERVAL_MS);
    policy.setMaxInterval(BACKOFF_MAX_INTERVAL_MS);
    policy.setMultiplier(BACKOFF_MULTIPLIER);
    return policy;
  }
}
