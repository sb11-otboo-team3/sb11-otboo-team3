package com.otboo.domain.feed.batch;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedCleanupScheduler {

  private final JobLauncher jobLauncher;
  private final Job feedCleanupJob;

  // 매일 새벽 3시에 삭제
  @Scheduled(cron = "0 0 3 * * *")
  public void runFeedCleanupJob() throws Exception {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLocalDateTime("runAt", LocalDateTime.now())
        .toJobParameters();

    jobLauncher.run(feedCleanupJob, jobParameters);
  }
}