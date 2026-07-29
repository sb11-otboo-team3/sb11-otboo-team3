package com.otboo.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;

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
        savedUser.getId(), savedUser.getRole().name(), savedUser.getTokenVersion() + 1
    );

    // when & then
    mockMvc.perform(get("/api/test/protected")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CSRF 토큰 없이 인증된 POST 요청을 보내면 403을 반환한다")
  void postWithoutCsrfTokenReturns403() throws Exception {
    // given
    User user = User.create("csrftest@otboo.io", "csrf테스트",
        passwordEncoder.encode("password1234"));
    User savedUser = userRepository.saveAndFlush(user);

    String token = jwtProvider.createAccessToken(
        savedUser.getId(), savedUser.getRole().name(), savedUser.getTokenVersion()
    );

    // when & then
    mockMvc.perform(post("/api/test/protected")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CSRF 토큰을 포함하면 인증된 POST 요청이 성공한다")
  void postWithCsrfTokenSucceeds() throws Exception {
    // given
    User user = User.create("csrftest2@otboo.io", "csrf테스트2",
        passwordEncoder.encode("password1234"));
    User savedUser = userRepository.saveAndFlush(user);

    String token = jwtProvider.createAccessToken(
        savedUser.getId(), savedUser.getRole().name(), savedUser.getTokenVersion()
    );

    // when & then
    mockMvc.perform(post("/api/test/protected")
            .header("Authorization", "Bearer " + token)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  // NOTE: 이 테스트만 전체 스위트와 같이 실행하면 CSRF 쿠키가
  // 응답에 반영되지 않는 격리 문제가 발생해 @DirtiesContext로
  // 컨텍스트를 강제로 새로 생성했습니다. 근본 원인(CookieCsrfTokenRepository
  // 또는 MockMvc의 상태 공유 방식으로 추정)은 아직 규명하지 못했습니다.
  @Test
  @DisplayName("CSRF 토큰 조회 시 XSRF-TOKEN 쿠키가 설정된다")
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  void csrfTokenEndpointSetsXsrfTokenCookie() throws Exception {
    mockMvc.perform(get("/api/auth/csrf-token"))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists("XSRF-TOKEN"));
  }

  @Test
  @DisplayName("CSRF 토큰 없이 로그인 요청을 보내면 403을 반환한다")
  void signInWithoutCsrfTokenReturns403() throws Exception {
    mockMvc.perform(post("/api/auth/sign-in")
            .param("username", "test@otboo.io")
            .param("password", "password1234"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CSRF 토큰을 포함하면 로그인 요청이 정상 처리된다")
  void signInWithCsrfTokenSucceeds() throws Exception {
    // given
    userRepository.saveAndFlush(User.create(
        "csrflogintest@otboo.io", "csrf로그인테스트",
        passwordEncoder.encode("password1234")
    ));

    // when & then
    mockMvc.perform(post("/api/auth/sign-in")
            .param("username", "csrflogintest@otboo.io")
            .param("password", "password1234")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("CSRF 토큰 없이 회원가입 요청을 보내면 403을 반환한다")
  void signUpWithoutCsrfTokenReturns403() throws Exception {
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"name":"csrf테스트","email":"csrfsignup1@otboo.io","password":"password1234"}
                            """))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CSRF 토큰을 포함하면 회원가입 요청이 정상 처리된다")
  void signUpWithCsrfTokenSucceeds() throws Exception {
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                            {"name":"csrf테스트2","email":"csrfsignup2@otboo.io","password":"password1234"}
                            """)
            .with(csrf()))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("발급받은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더에 실어 보내면 요청이 성공한다")
  void csrfCookieAndHeaderFlowSucceeds() throws Exception {
    // given: 먼저 CSRF 토큰을 발급받아 쿠키를 확보
    MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf-token"))
        .andExpect(status().isNoContent())
        .andReturn();

    Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrfCookie).isNotNull();
    String csrfTokenValue = xsrfCookie.getValue();

    User user = User.create("csrfflowtest@otboo.io", "csrf플로우테스트",
        passwordEncoder.encode("password1234"));
    User savedUser = userRepository.saveAndFlush(user);

    String jwt = jwtProvider.createAccessToken(
        savedUser.getId(), savedUser.getRole().name(), savedUser.getTokenVersion()
    );

    // when & then: 쿠키와 헤더에 같은 값을 실어서 요청
    mockMvc.perform(post("/api/test/protected")
            .header("Authorization", "Bearer " + jwt)
            .cookie(xsrfCookie)
            .header("X-XSRF-TOKEN", csrfTokenValue))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("쿠키 값과 다른 X-XSRF-TOKEN 헤더를 보내면 403을 반환한다")
  void mismatchedCsrfCookieAndHeaderReturns403() throws Exception {
    // given
    MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf-token"))
        .andExpect(status().isNoContent())
        .andReturn();

    Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrfCookie).isNotNull();

    User user = User.create("csrfmismatch@otboo.io", "csrf불일치테스트",
        passwordEncoder.encode("password1234"));
    User savedUser = userRepository.saveAndFlush(user);

    String jwt = jwtProvider.createAccessToken(
        savedUser.getId(), savedUser.getRole().name(), savedUser.getTokenVersion()
    );

    // when & then: 쿠키는 진짜, 헤더는 가짜 값
    mockMvc.perform(post("/api/test/protected")
            .header("Authorization", "Bearer " + jwt)
            .cookie(xsrfCookie)
            .header("X-XSRF-TOKEN", "wrong-token-value"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("쿠키는 있지만 X-XSRF-TOKEN 헤더가 없으면 403을 반환한다")
  void csrfCookiePresentButHeaderMissingReturns403() throws Exception {
    // given
    MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf-token"))
        .andExpect(status().isNoContent())
        .andReturn();

    Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrfCookie).isNotNull();

    User user = User.create("csrfnoheader@otboo.io", "csrf헤더누락테스트",
        passwordEncoder.encode("password1234"));
    User savedUser = userRepository.saveAndFlush(user);

    String jwt = jwtProvider.createAccessToken(
        savedUser.getId(), savedUser.getRole().name(), savedUser.getTokenVersion()
    );

    // when & then: 쿠키는 있지만 헤더를 안 보냄
    mockMvc.perform(post("/api/test/protected")
            .header("Authorization", "Bearer " + jwt)
            .cookie(xsrfCookie))
        .andExpect(status().isForbidden());
  }
}