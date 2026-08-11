package com.otboo.domain.weather.batch;

import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

// 오래된 날씨 데이터(3일 이전, 피드가 들고 있지 않은 것만)를 지운다. WeatherRepository.deleteBatchOlderThan 참고.
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WeatherCleanupJobConfig {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int RETENTION_DAYS = 3;
  private static final int DELETE_BATCH_SIZE = 500;

  private final WeatherRepository weatherRepository;
  private final Clock clock;

  @Bean
  public Job weatherCleanupJob(JobRepository jobRepository, Step weatherCleanupStep) {
    return new JobBuilder("weatherCleanupJob", jobRepository)
        .start(weatherCleanupStep)
        .build();
  }

  @Bean
  public Step weatherCleanupStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager
  ) {
    return new StepBuilder("weatherCleanupStep", jobRepository)
        .tasklet((contribution, chunkContext) -> {
          Instant cutoff = LocalDate.now(clock).minusDays(RETENTION_DAYS).atStartOfDay(KST).toInstant();
          int deleted = weatherRepository.deleteBatchOlderThan(cutoff, DELETE_BATCH_SIZE);
          contribution.incrementWriteCount(deleted);
          log.info("삭제된 날씨 데이터 수: {}, cutoff={}", deleted, cutoff);

          // 이번 호출이 batchSize만큼 꽉 채워 지웠다면 더 남아있을 수 있으니, 새 트랜잭션으로 tasklet을
          // 다시 호출한다(CONTINUABLE). 매번 "지금 기준 조건에 맞는 다음 N개"를 새로 찾기 때문에,
          // OFFSET 페이징과 달리 삭제로 인해 뒤 행들이 앞으로 밀려서 스킵되는 문제가 없다.
          return deleted < DELETE_BATCH_SIZE ? RepeatStatus.FINISHED : RepeatStatus.CONTINUABLE;
        }, transactionManager)
        .build();
  }
}
