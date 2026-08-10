package com.otboo.global.config;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
  @Transactional
  public void run(String... args) {
    if (userRepository.existsByRole(UserRole.ADMIN)) {
      log.info("이미 ADMIN 계정이 존재하여 초기화를 건너뜁니다.");
      return;
    }

    String normalizedEmail = adminProperties.initEmail().toLowerCase(java.util.Locale.ROOT);

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

    userRepository.save(admin);

    log.info("초기 어드민 계정을 생성했습니다. email={}", normalizedEmail);
  }
}