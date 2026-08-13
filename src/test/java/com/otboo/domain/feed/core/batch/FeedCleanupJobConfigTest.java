package com.otboo.domain.feed.core.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.otboo.domain.feed.core.repository.FeedRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;

class FeedCleanupJobConfigTest {

  @Test
  @DisplayName("피드 정리 Job을 생성한다")
  void feedCleanupJob_success() {
    FeedRepository feedRepository = mock(FeedRepository.class);
    FeedCleanupJobConfig config = new FeedCleanupJobConfig(feedRepository);

    JobRepository jobRepository = mock(JobRepository.class);
    Step step = mock(Step.class);

    Job job = config.feedCleanupJob(jobRepository, step);

    assertThat(job).isNotNull();
    assertThat(job.getName()).isEqualTo("feedCleanupJob");
  }
}