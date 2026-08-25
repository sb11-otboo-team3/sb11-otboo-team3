package com.otboo.domain.directmessage.batch;

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
public class DirectMessageCleanupScheduler {

  private final JobLauncher jobLauncher;
  private final Job directMessageCleanupJob;

  // 매일 21시 15분 삭제
  @Scheduled(cron = "0 15 21 * * *", zone = "Asia/Seoul")
  public void runDirectMessageCleanupJob() throws Exception {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLocalDateTime("runAt", LocalDateTime.now())
        .toJobParameters();

    jobLauncher.run(directMessageCleanupJob, jobParameters);
  }
}