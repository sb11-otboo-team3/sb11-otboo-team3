package com.otboo.domain.directmessage.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

@ExtendWith(MockitoExtension.class)
class DirectMessageCleanupSchedulerTest {

  @Mock
  private JobLauncher jobLauncher;

  @Mock
  private Job directMessageCleanupJob;

  @InjectMocks
  private DirectMessageCleanupScheduler directMessageCleanupScheduler;

  @Test
  @DisplayName("DM 정리 스케줄러는 JobLauncher로 정리 Job을 실행한다")
  void runDirectMessageCleanupJob_success() throws Exception {
    directMessageCleanupScheduler.runDirectMessageCleanupJob();

    verify(jobLauncher).run(eq(directMessageCleanupJob), any(JobParameters.class));
  }
}