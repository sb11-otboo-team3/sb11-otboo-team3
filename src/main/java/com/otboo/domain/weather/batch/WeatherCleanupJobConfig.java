package com.otboo.domain.weather.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

// 오래된 날씨 데이터(3일 이전, 피드가 들고 있지 않은 것만)를 지운다. 실제 삭제 로직은 WeatherCleanupTasklet 참고
// (반복 삭제 + 최대 반복 횟수 제한이 있어서 별도 클래스로 뺐음 - 단위 테스트하기 위함).
@Configuration
@RequiredArgsConstructor
public class WeatherCleanupJobConfig {

  @Bean
  public Job weatherCleanupJob(JobRepository jobRepository, Step weatherCleanupStep) {
    return new JobBuilder("weatherCleanupJob", jobRepository)
        .start(weatherCleanupStep)
        .build();
  }

  @Bean
  public Step weatherCleanupStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      WeatherCleanupTasklet weatherCleanupTasklet
  ) {
    return new StepBuilder("weatherCleanupStep", jobRepository)
        .tasklet(weatherCleanupTasklet, transactionManager)
        .build();
  }
}
