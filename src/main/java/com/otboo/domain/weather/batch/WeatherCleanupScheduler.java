package com.otboo.domain.weather.batch;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherCleanupScheduler {

  private final JobLauncher jobLauncher;
  private final Job weatherCleanupJob;

  // 매일 20시 45분(KST)에 삭제
  // 서버 기본 TZ와 관계없이 한국 시각 20:45에 실행한다.
  // 프로퍼티로 분리하여 필요 시 실행 주기를 오버라이드할 수 있다.
  // 기본값은 운영과 동일한 20시 45분
  @Scheduled(cron = "${weather.cleanup.cron:0 45 20 * * *}", zone = "Asia/Seoul")
  // 서버 2대라 cron이 두 인스턴스에서 동시에 발화할 수 있음 - ShedLock으로 한 인스턴스만 실제로 돌게 함.
  @SchedulerLock(name = "weatherCleanupJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void runWeatherCleanupJob() throws Exception {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLocalDateTime("runAt", LocalDateTime.now())
        .toJobParameters();

    jobLauncher.run(weatherCleanupJob, jobParameters);
  }
}
