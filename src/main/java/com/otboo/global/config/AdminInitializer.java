package com.otboo.global.config;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminInitializer implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AdminProperties adminProperties;

  @Override
  public void run(String... args) {
    try {
      initializeAdmin();
    } catch (Exception e) {
      log.error("초기 어드민 계정 생성 중 오류가 발생했습니다. 애플리케이션은 정상 기동되며, 수동 확인이 필요합니다.", e);
    }
  }

  @Transactional
  public void initializeAdmin() {
    if (userRepository.existsByRole(UserRole.ADMIN)) {
      log.info("이미 ADMIN 계정이 존재하여 초기화를 건너뜁니다.");
      return;
    }

    String normalizedEmail = adminProperties.initEmail().toLowerCase(Locale.ROOT);

    if (userRepository.existsByEmail(normalizedEmail)) {
      log.warn(
          "초기 어드민 이메일({})이 이미 다른 계정으로 등록되어 있어 초기화를 건너뜁니다.",
          normalizedEmail
      );
      return;
    }

    String passwordHash = passwordEncoder.encode(adminProperties.initPassword());
    User admin = User.create(normalizedEmail, adminProperties.initName(), passwordHash);
    admin.changeRole(UserRole.ADMIN);

    try {
      userRepository.save(admin);
      log.info("초기 어드민 계정을 생성했습니다. email={}", normalizedEmail);
    } catch (DataIntegrityViolationException e) {
      log.info(
          "다른 인스턴스가 동시에 초기 어드민 계정을 생성하여 건너뜁니다. email={}",
          normalizedEmail
      );
    }
  }
}