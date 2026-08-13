package com.otboo.domain.directmessage.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.otboo.domain.directmessage.repository.DirectMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;

class DirectMessageCleanupJobConfigTest {

  @Test
  @DisplayName("DM 정리 Job을 생성한다")
  void directMessageCleanupJob_success() {
    DirectMessageRepository directMessageRepository = mock(DirectMessageRepository.class);
    DirectMessageCleanupJobConfig config =
        new DirectMessageCleanupJobConfig(directMessageRepository);

    JobRepository jobRepository = mock(JobRepository.class);
    Step step = mock(Step.class);

    Job job = config.directMessageCleanupJob(jobRepository, step);

    assertThat(job).isNotNull();
    assertThat(job.getName()).isEqualTo("directMessageCleanupJob");
  }
}