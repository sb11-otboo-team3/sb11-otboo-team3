package com.otboo.domain.weather.batch;

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
public class WeatherPrefetchScheduler {

  private final JobLauncher jobLauncher;
  private final Job weatherPrefetchJob;

  // 기상청 발표 시각(02,05,08,11,14,17,20,23시) + 15분마다 실행. zone을 명시해서 배포 환경의 서버 기본
  // TZ가 뭐든 항상 한국 시각 기준 발표+15분에 돌게 한다.
  @Scheduled(cron = "0 15 2,5,8,11,14,17,20,23 * * *", zone = "Asia/Seoul")
  public void runWeatherPrefetchJob() throws Exception {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLocalDateTime("runAt", LocalDateTime.now())
        .toJobParameters();

    jobLauncher.run(weatherPrefetchJob, jobParameters);
  }
}
