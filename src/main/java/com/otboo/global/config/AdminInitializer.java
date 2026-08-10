package com.otboo.global.config;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminInitializer implements CommandLineRunner {

  private final AdminInitializationService adminInitializationService;

  @Override
  public void run(String... args) {
    try {
      adminInitializationService.initializeAdmin();
    } catch (Exception e) {
      log.error("초기 어드민 계정 생성 중 오류가 발생했습니다. 애플리케이션은 정상 기동되며, 수동 확인이 필요합니다.", e);
    }
  }
}