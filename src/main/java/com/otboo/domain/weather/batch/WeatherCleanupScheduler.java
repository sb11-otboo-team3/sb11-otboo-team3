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

  // 매일 새벽 4시(KST)에 삭제 - DM 정리(2시)/피드 정리(3시)와 겹치지 않게. zone을 명시해서 배포 환경의
  // 서버 기본 TZ가 뭐든 항상 한국 새벽 4시에 돌게 한다.
  // 프로퍼티로 뺀 이유: local 프로필에서만 주기를 짧게 오버라이드해서 테스트할 수 있게 하기 위함
  // (application-local.yaml의 weather.cleanup.cron 참고) - 기본값은 운영과 동일한 새벽 4시.
  @Scheduled(cron = "${weather.cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
  // 서버 2대라 cron이 두 인스턴스에서 동시에 발화할 수 있음 - ShedLock으로 한 인스턴스만 실제로 돌게 함.
  @SchedulerLock(name = "weatherCleanupJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void runWeatherCleanupJob() throws Exception {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLocalDateTime("runAt", LocalDateTime.now())
        .toJobParameters();

    jobLauncher.run(weatherCleanupJob, jobParameters);
  }
}
