package com.otboo.domain.directmessage.batch;

import com.otboo.domain.directmessage.repository.DirectMessageRepository;
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
public class DirectMessageCleanupJobConfig {

  private final DirectMessageRepository directMessageRepository;

  @Bean
  public Job directMessageCleanupJob(
      JobRepository jobRepository,
      Step directMessageCleanupStep
  ) {
    return new JobBuilder("directMessageCleanupJob", jobRepository)
        .start(directMessageCleanupStep)
        .build();
  }

  @Bean
  public Step directMessageCleanupStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager
  ) {
    return new StepBuilder("directMessageCleanupStep", jobRepository)
        .tasklet((contribution, chunkContext) -> {
          long deletedCount = directMessageRepository.deleteMessages();

          log.info("삭제된 DM 메시지 수: {}", deletedCount);

          return RepeatStatus.FINISHED;
        }, transactionManager)
        .build();
  }

}
