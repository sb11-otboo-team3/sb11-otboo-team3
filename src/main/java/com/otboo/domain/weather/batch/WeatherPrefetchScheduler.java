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
public class WeatherPrefetchScheduler {

  private final JobLauncher jobLauncher;
  private final Job weatherPrefetchJob;

  // 기상청 발표 시각(02,05,08,11,14,17,20,23시) + 15분마다 실행. zone을 명시해서 배포 환경의 서버 기본
  // TZ가 뭐든 항상 한국 시각 기준 발표+15분에 돌게 한다.
  // 프로퍼티로 뺀 이유: local 프로필에서만 주기를 짧게 오버라이드해서 테스트할 수 있게 하기 위함
  // (application-local.yaml의 weather.prefetch.cron 참고) - 기본값은 운영과 동일.
  @Scheduled(cron = "${weather.prefetch.cron:0 15 5,8,11,14,17,20 * * *}", zone = "Asia/Seoul")
  // 서버 2대라 cron이 두 인스턴스에서 동시에 발화할 수 있음 - ShedLock으로 한 인스턴스만 실제로 돌게 함.
  // lockAtMostFor(30분) 근거: 격자 하나가 재시도까지 다 실패하는 최악의 경우 ~30초, 동시성 10(
  // WeatherPrefetchJobConfig.CONCURRENCY)으로 나눠 처리하면 활성 격자 500개까지도 약 25분 안에
  // 끝난다는 계산 - 그보다 넉넉하게 30분으로 잡음.
  @SchedulerLock(name = "weatherPrefetchJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
  public void runWeatherPrefetchJob() throws Exception {
    JobParameters jobParameters = new JobParametersBuilder()
        .addLocalDateTime("runAt", LocalDateTime.now())
        .toJobParameters();

    jobLauncher.run(weatherPrefetchJob, jobParameters);
  }
}
