package com.otboo.domain.feed.batch;

import com.otboo.domain.feed.repository.FeedRepository;
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

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FeedCleanupJobConfig {

  private final FeedRepository feedRepository;

  @Bean
  public Job feedCleanupJob(
      JobRepository jobRepository,
      Step feedCleanupStep
  ) {
    return new JobBuilder("feedCleanupJob", jobRepository)
        .start(feedCleanupStep)
        .build();
  }

  @Bean
  public Step feedCleanupStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager
  ) {
    return new StepBuilder("feedCleanupStep", jobRepository)
        .tasklet((contribution, chunkContext) -> {
          long deletedCount = feedRepository.deleteFeedsDeletedBeforeOneDay();

          log.info("물리 삭제된 피드 수: {}", deletedCount);

          return RepeatStatus.FINISHED;
        }, transactionManager)
        .build();
  }
}