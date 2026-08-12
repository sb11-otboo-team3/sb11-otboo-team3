package com.otboo.domain.weather.batch;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

// 프리페치 배치가 끝날 때마다 수집 건수/실패율/소요시간을 Micrometer로 발행한다. GridForecastWriter나
// skip 리스너 곳곳에 카운터를 직접 심는 대신, Spring Batch가 StepExecution에 이미 집계해둔
// writeCount(성공 처리된 격자 수)/processSkipCount(재시도까지 다 실패해서 스킵된 격자 수)를
// job 종료 시점에 한 번에 읽어서 발행하는 방식을 쓴다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherPrefetchMetricsListener implements JobExecutionListener {

  private static final String STEP_NAME = "weatherPrefetchStep";
  private static final String COLLECTED_COUNTER = "weather.prefetch.grid.collected";
  private static final String FAILED_COUNTER = "weather.prefetch.grid.failed";
  private static final String FAILURE_RATE_GAUGE = "weather.prefetch.failure.rate";
  private static final String JOB_DURATION_TIMER = "weather.prefetch.job.duration";

  private final MeterRegistry meterRegistry;
  // 실패율은 카운터(누적)가 아니라 "가장 최근 실행 기준" 값이라 Gauge로 발행한다 - Gauge는 등록 시점에
  // 값을 한 번 박아두는 게 아니라, 스크레이프될 때마다 이 참조가 들고 있는 현재값을 읽어간다.
  private final AtomicReference<Double> lastFailureRate = new AtomicReference<>(0.0);

  @PostConstruct
  void registerFailureRateGauge() {
    Gauge.builder(FAILURE_RATE_GAUGE, lastFailureRate, AtomicReference::get)
        .description("가장 최근 프리페치 배치 실행에서 실패(재시도 소진 후 skip)한 격자 비율")
        .register(meterRegistry);
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    findStep(jobExecution).ifPresentOrElse(
        step -> record(jobExecution, step),
        () -> log.warn("프리페치 배치 메트릭 - {} StepExecution을 못 찾음, 메트릭 기록 스킵", STEP_NAME)
    );
  }

  private void record(JobExecution jobExecution, StepExecution step) {
    long collected = step.getWriteCount();
    long failed = step.getProcessSkipCount();
    long total = collected + failed;

    meterRegistry.counter(COLLECTED_COUNTER).increment(collected);
    meterRegistry.counter(FAILED_COUNTER).increment(failed);
    lastFailureRate.set(total == 0 ? 0.0 : (double) failed / total);

    if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
      Duration duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
      Timer.builder(JOB_DURATION_TIMER)
          .tag("status", jobExecution.getExitStatus().getExitCode())
          .register(meterRegistry)
          .record(duration);
    }

    log.info("프리페치 배치 메트릭 - 수집={}, 실패={}, 실패율={}", collected, failed, lastFailureRate.get());
  }

  private Optional<StepExecution> findStep(JobExecution jobExecution) {
    return jobExecution.getStepExecutions().stream()
        .filter(se -> se.getStepName().equals(STEP_NAME))
        .findFirst();
  }
}
