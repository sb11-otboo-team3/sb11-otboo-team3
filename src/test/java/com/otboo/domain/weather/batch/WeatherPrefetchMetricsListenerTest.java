package com.otboo.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

// GridForecastWriter/skip 리스너 곳곳에 카운터를 직접 심는 대신, Spring Batch가 StepExecution에
// 이미 집계해둔 writeCount(성공)/processSkipCount(실패)를 job 종료 시점에 한 번에 읽어서 발행하는
// 방식이라 - Spring 컨텍스트 없이 순수 POJO(JobExecution/StepExecution)로 검증 가능.
class WeatherPrefetchMetricsListenerTest {

  private SimpleMeterRegistry meterRegistry;
  private WeatherPrefetchMetricsListener listener;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    listener = new WeatherPrefetchMetricsListener(meterRegistry);
    listener.registerFailureRateGauge();
  }

  private JobExecution jobExecutionWith(long writeCount, long processSkipCount, ExitStatus exitStatus) {
    JobInstance jobInstance = new JobInstance(1L, "weatherPrefetchJob");
    JobExecution jobExecution = new JobExecution(jobInstance, 1L, new JobParameters());
    jobExecution.setStartTime(LocalDateTime.of(2026, 8, 11, 2, 15, 0));
    jobExecution.setEndTime(LocalDateTime.of(2026, 8, 11, 2, 15, 5));
    jobExecution.setExitStatus(exitStatus);

    StepExecution stepExecution = jobExecution.createStepExecution("weatherPrefetchStep");
    stepExecution.setWriteCount(writeCount);
    stepExecution.setProcessSkipCount(processSkipCount);
    return jobExecution;
  }

  @Test
  @DisplayName("성공/실패 격자 수를 카운터로 발행한다")
  void publishesCollectedAndFailedCounters() {
    // when
    listener.afterJob(jobExecutionWith(8, 2, ExitStatus.COMPLETED));

    // then
    assertThat(meterRegistry.counter("weather.prefetch.grid.collected").count()).isEqualTo(8.0);
    assertThat(meterRegistry.counter("weather.prefetch.grid.failed").count()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("실패율을 게이지로 발행한다")
  void publishesFailureRateGauge() {
    // when
    listener.afterJob(jobExecutionWith(8, 2, ExitStatus.COMPLETED));

    // then
    assertThat(meterRegistry.get("weather.prefetch.failure.rate").gauge().value()).isEqualTo(0.2);
  }

  @Test
  @DisplayName("처리한 격자가 하나도 없으면(collected=0, failed=0) 실패율은 0으로 나눗셈 오류 없이 처리한다")
  void failureRateIsZeroWhenNothingProcessed() {
    // when
    listener.afterJob(jobExecutionWith(0, 0, ExitStatus.COMPLETED));

    // then
    assertThat(meterRegistry.get("weather.prefetch.failure.rate").gauge().value()).isZero();
  }

  @Test
  @DisplayName("job 시작~종료 시각 차이를 소요시간 타이머로 기록한다")
  void recordsJobDurationTimer() {
    // when
    listener.afterJob(jobExecutionWith(8, 2, ExitStatus.COMPLETED));

    // then
    Timer timer = meterRegistry.find("weather.prefetch.job.duration").tag("status", "COMPLETED").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(5.0);
  }

  @Test
  @DisplayName("여러 번 실행하면 카운터는 누적되고, 실패율 게이지는 가장 최근 실행 값으로 갱신된다")
  void countersAccumulateAcrossRunsButGaugeReflectsLatestRun() {
    // when: 1차 실행(수집 8, 실패 2) 후 2차 실행(수집 5, 실패 5)
    listener.afterJob(jobExecutionWith(8, 2, ExitStatus.COMPLETED));
    listener.afterJob(jobExecutionWith(5, 5, ExitStatus.COMPLETED));

    // then: 카운터는 두 번의 합산(13, 7)
    assertThat(meterRegistry.counter("weather.prefetch.grid.collected").count()).isEqualTo(13.0);
    assertThat(meterRegistry.counter("weather.prefetch.grid.failed").count()).isEqualTo(7.0);
    // 실패율 게이지는 가장 최근 실행(5/10 = 0.5) 기준
    assertThat(meterRegistry.get("weather.prefetch.failure.rate").gauge().value()).isEqualTo(0.5);
  }

  @Test
  @DisplayName("weatherPrefetchStep을 못 찾으면 예외 없이 조용히 스킵하고 메트릭을 기록하지 않는다")
  void skipsSilentlyWhenStepNotFound() {
    // given: 다른 이름의 Step만 있는 JobExecution
    JobInstance jobInstance = new JobInstance(1L, "weatherPrefetchJob");
    JobExecution jobExecution = new JobExecution(jobInstance, 1L, new JobParameters());
    jobExecution.setStartTime(LocalDateTime.now());
    jobExecution.setEndTime(LocalDateTime.now());
    jobExecution.setExitStatus(ExitStatus.COMPLETED);
    jobExecution.createStepExecution("someOtherStep");

    // when
    listener.afterJob(jobExecution);

    // then
    assertThat(meterRegistry.find("weather.prefetch.grid.collected").counter()).isNull();
  }
}
