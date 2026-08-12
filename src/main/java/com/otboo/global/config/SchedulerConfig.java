package com.otboo.global.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
// defaultLockAtMostFor: 개별 @SchedulerLock에서 안 정해주면 이 값을 씀 - 락을 쥔 인스턴스가 죽어도
// 이 시간 지나면 자동으로 락이 풀려서, 락이 영원히 안 풀리는 사태(고아 락)를 막는 안전장치.
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerConfig {

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    // usingDbTime(): 락 만료 시각을 각 서버의 로컬 시계가 아니라 DB 서버 시간 기준으로 비교한다 -
    // 서버 여러 대의 시계가 (NTP 오차 등으로) 살짝 어긋나 있어도 락 판단이 흔들리지 않는다.
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }
}