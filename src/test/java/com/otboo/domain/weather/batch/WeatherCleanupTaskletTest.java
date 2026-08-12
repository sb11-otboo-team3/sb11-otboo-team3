package com.otboo.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;

import com.otboo.domain.weather.repository.WeatherRepository;

// CONTINUABLE 반복에 상한이 없으면, 지울 게 아주 많이 쌓였을 때 ShedLock의 lockAtMostFor보다 오래 실행돼
// 락이 만료되고 다음 트리거가 같은 작업을 중복 실행할 수 있다는 리뷰 지적 - 반복 횟수를 캡 걸어서
// 한 번의 실행 소요시간을 예측 가능한 범위로 묶는다. 남는 건 다음 스케줄 실행이 이어서 처리.
@ExtendWith(MockitoExtension.class)
class WeatherCleanupTaskletTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private StepContribution contribution;

  private WeatherCleanupTasklet tasklet;
  private Clock clock;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(LocalDateTime.of(2026, 8, 12, 9, 0).atZone(KST).toInstant(), KST);
    tasklet = new WeatherCleanupTasklet(weatherRepository, clock);
  }

  private ChunkContext newChunkContext() {
    StepExecution stepExecution = new StepExecution("weatherCleanupStep", new JobExecution(1L));
    return new ChunkContext(new StepContext(stepExecution));
  }

  @Test
  @DisplayName("삭제된 개수가 배치 크기보다 적으면(더 지울 게 없다는 뜻) FINISHED를 리턴한다")
  void returnsFinishedWhenDeletedLessThanBatchSize() throws Exception {
    // given
    given(weatherRepository.deleteBatchOlderThan(any(), eq(WeatherCleanupTasklet.DELETE_BATCH_SIZE)))
        .willReturn(200);

    // when
    RepeatStatus result = tasklet.execute(contribution, newChunkContext());

    // then
    assertThat(result).isEqualTo(RepeatStatus.FINISHED);
  }

  @Test
  @DisplayName("삭제된 개수가 배치 크기와 같으면(더 남아있을 수 있음) CONTINUABLE을 리턴한다")
  void returnsContinuableWhenDeletedEqualsBatchSize() throws Exception {
    // given
    given(weatherRepository.deleteBatchOlderThan(any(), eq(WeatherCleanupTasklet.DELETE_BATCH_SIZE)))
        .willReturn(WeatherCleanupTasklet.DELETE_BATCH_SIZE);

    // when
    RepeatStatus result = tasklet.execute(contribution, newChunkContext());

    // then
    assertThat(result).isEqualTo(RepeatStatus.CONTINUABLE);
  }

  @Test
  @DisplayName("계속 배치 크기만큼 지워지고 있어도 최대 반복 횟수에 도달하면 FINISHED로 끝낸다")
  void stopsAtMaxIterationsEvenIfStillFull() throws Exception {
    // given: 매번 딱 배치 크기만큼 지워짐 - 무한정 CONTINUABLE일 수 있는 상황을 재현
    given(weatherRepository.deleteBatchOlderThan(any(), eq(WeatherCleanupTasklet.DELETE_BATCH_SIZE)))
        .willReturn(WeatherCleanupTasklet.DELETE_BATCH_SIZE);
    ChunkContext chunkContext = newChunkContext(); // 같은 StepExecution을 계속 재사용(실제 Job 실행과 동일)

    // when: 최대 반복 횟수 - 1번까지는 계속 CONTINUABLE
    for (int i = 1; i < WeatherCleanupTasklet.MAX_ITERATIONS; i++) {
      RepeatStatus result = tasklet.execute(contribution, chunkContext);
      assertThat(result).as("iteration %d", i).isEqualTo(RepeatStatus.CONTINUABLE);
    }

    // then: 마지막(MAX_ITERATIONS번째) 호출은 FINISHED로 끝남
    RepeatStatus lastResult = tasklet.execute(contribution, chunkContext);
    assertThat(lastResult).isEqualTo(RepeatStatus.FINISHED);
    verify(weatherRepository, times(WeatherCleanupTasklet.MAX_ITERATIONS))
        .deleteBatchOlderThan(any(), eq(WeatherCleanupTasklet.DELETE_BATCH_SIZE));
  }

  @Test
  @DisplayName("cutoff는 오늘(KST) 자정에서 3일 전으로 계산해서 넘긴다")
  void passesCutoffThreeDaysBeforeTodayMidnight() throws Exception {
    // given
    given(weatherRepository.deleteBatchOlderThan(any(), anyInt())).willReturn(0);

    // when
    tasklet.execute(contribution, newChunkContext());

    // then: 2026-08-12 KST 자정 - 3일 = 2026-08-09 KST 자정
    var expectedCutoff = LocalDateTime.of(2026, 8, 9, 0, 0).atZone(KST).toInstant();
    verify(weatherRepository).deleteBatchOlderThan(eq(expectedCutoff), anyInt());
  }
}
