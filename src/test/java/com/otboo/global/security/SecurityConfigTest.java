package com.otboo.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.domain.auth.jwt.JwtProvider;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtProvider jwtProvider;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("permitAll 대상이 아닌 요청은 인증 없이 접근 시 401을 반환한다")
  void protectedEndpointRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/test/protected"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /api/users는 인증 없이 접근하면 401을 반환한다")
  void getUserListRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/users"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("정상 JWT를 포함하면 보호된 API에 접근할 수 있다")
  void validJwtAllowsAccessToProtectedEndpoint() throws Exception {
    // given
    User user = User.create("filtertest@otboo.io", "필터테스트",
        passwordEncoder.encode("password1234"));
    User savedUser = userRepository.saveAndFlush(user);

    String token = jwtProvider.createAccessToken(
        savedUser.getId(), savedUser.getRole().name(), savedUser.getTokenVersion()
    );

    // when & then
    mockMvc.perform(get("/api/test/protected")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("잘못된 형식의 JWT는 401을 반환한다")
  void invalidJwtFormatReturns401() throws Exception {
    mockMvc.perform(get("/api/test/protected")
            .header("Authorization", "Bearer invalid-token-format"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("tokenVersion이 일치하지 않는 JWT는 401을 반환한다")
  void tokenVersionMismatchReturns401() throws Exception {
    // given
    User user = User.create("versiontest@otboo.io", "버전테스트",
        passwordEncoder.encode("password1234"));
    User savedUser = userRepository.saveAndFlush(user);

    String token = jwtProvider.createAccessToken(
        savedUser.getId(), savedUser.getRole().name(), savedUser.getTokenVersion()
    );

    savedUser.lock();
    userRepository.saveAndFlush(savedUser);

    // when & then
    mockMvc.perform(get("/api/test/protected")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }
}