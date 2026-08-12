package com.otboo.domain.weather.batch;

import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

// 오래된 날씨 데이터를 LIMIT 단위로 반복 삭제한다(WeatherRepository.deleteBatchOlderThan 참고).
// 반복 횟수에 상한(MAX_ITERATIONS)을 둬서, 지울 게 아무리 많이 쌓여있어도 이번 Job 실행의 소요시간이
// 예측 가능한 범위로 묶이게 한다 - 안 그러면 ShedLock의 lockAtMostFor보다 오래 실행될 수 있고, 그러면
// 락이 만료돼서 다음 스케줄 트리거가 같은 Job을 중복 실행할 수 있다. 캡을 넘는 나머지는 다음 실행이
// 이어서 처리한다(매번 "지금 기준 조건에 맞는 다음 N개"를 새로 찾으므로 안전).
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCleanupTasklet implements Tasklet {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String ITERATION_KEY = "weatherCleanup.iteration";

  static final int RETENTION_DAYS = 3;
  static final int DELETE_BATCH_SIZE = 500;
  static final int MAX_ITERATIONS = 100;

  private final WeatherRepository weatherRepository;
  private final Clock clock;

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    // ExecutionContext는 Spring Batch가 같은 StepExecution에 대해 CONTINUABLE로 tasklet을 다시 호출할
    // 때도 그대로 유지해주는 상태 저장소라, 반복 횟수를 여기 담아두면 이번 Job 실행 동안만 정확히 셀 수 있다.
    ExecutionContext executionContext = chunkContext.getStepContext().getStepExecution().getExecutionContext();
    int iteration = executionContext.getInt(ITERATION_KEY, 0) + 1;
    executionContext.putInt(ITERATION_KEY, iteration);

    // LocalDate.now(clock)은 clock 자신의 zone으로 "오늘 날짜"를 판단한다 - clock이 KST가 아닌 다른
    // zone이면(예: UTC) 자정~9시 KST 구간에서 하루 이른 날짜가 나올 수 있다. clock.withZone(KST)로
    // KST 기준 날짜를 명시적으로 못박아서, clock 빈의 zone 설정과 무관하게 항상 정확하게 만든다.
    Instant cutoff = LocalDate.now(clock.withZone(KST)).minusDays(RETENTION_DAYS).atStartOfDay(KST).toInstant();
    int deleted = weatherRepository.deleteBatchOlderThan(cutoff, DELETE_BATCH_SIZE);
    contribution.incrementWriteCount(deleted);
    log.info("삭제된 날씨 데이터 수: {}, cutoff={}, iteration={}", deleted, cutoff, iteration);

    if (deleted < DELETE_BATCH_SIZE) {
      return RepeatStatus.FINISHED;
    }
    if (iteration >= MAX_ITERATIONS) {
      log.warn("정리 배치 - 최대 반복 횟수({}) 도달, 남은 데이터는 다음 실행에서 이어서 처리됨", MAX_ITERATIONS);
      return RepeatStatus.FINISHED;
    }
    return RepeatStatus.CONTINUABLE;
  }
}
