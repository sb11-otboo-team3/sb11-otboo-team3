package com.otboo.domain.user.init;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInitializationService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AdminProperties adminProperties;

  public void initializeAdmin() {

    String normalizedEmail = adminProperties.initEmail().toLowerCase(Locale.ROOT);

    if (userRepository.existsByEmail(normalizedEmail)) {
      log.info("초기 어드민 이메일이 이미 등록되어 있어 초기화를 건너뜁니다.");
      return;
    }

    String passwordHash = passwordEncoder.encode(adminProperties.initPassword());
    User admin = User.create(normalizedEmail, adminProperties.initName(), passwordHash);
    admin.changeRole(UserRole.ADMIN);

    try {
      userRepository.saveAndFlush(admin);
      log.info("초기 어드민 계정을 생성했습니다.");

    } catch (DataIntegrityViolationException e) {

      Optional<User> concurrentUser =
              userRepository.findByEmail(normalizedEmail);

      if (concurrentUser.isPresent()
              && concurrentUser.get().getRole() == UserRole.ADMIN) {

        log.info(
                "다른 인스턴스가 동시에 초기 어드민 계정을 생성하여 건너뜁니다."
        );
        return;
      }

      throw e;
    }
  }
}