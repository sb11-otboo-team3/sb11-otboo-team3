package com.otboo.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import com.otboo.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock
  private JwtProvider jwtProvider;

  @Mock
  private UserRepository userRepository;

  @Mock
  private FilterChain filterChain;

  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new JwtAuthenticationFilter(jwtProvider, userRepository);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("정상 토큰이면 SecurityContext에 인증 정보가 설정된다")
  void validTokenSetsAuthentication() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    ReflectionTestUtils.setField(user, "id", userId);

    String token = "valid-token";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    given(jwtProvider.isValid(token)).willReturn(true);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(jwtProvider.getTokenVersion(token)).willReturn(0L);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // when
    filter.doFilter(request, response, filterChain);

    // then
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getPrincipal()).isEqualTo(userId);
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_USER");
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Authorization 헤더가 없으면 인증되지 않는다")
  void noAuthorizationHeaderDoesNotAuthenticate() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("Bearer 접두어가 없으면 인증되지 않는다")
  void tokenWithoutBearerPrefixDoesNotAuthenticate() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "InvalidPrefix some-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("유효하지 않은 토큰이면 인증되지 않는다")
  void invalidTokenDoesNotAuthenticate() throws Exception {
    // given
    String token = "invalid-token";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    given(jwtProvider.isValid(token)).willReturn(false);

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName("존재하지 않는 사용자면 인증되지 않는다")
  void nonExistentUserDoesNotAuthenticate() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    String token = "valid-token";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    given(jwtProvider.isValid(token)).willReturn(true);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("tokenVersion이 일치하지 않으면 인증되지 않는다")
  void tokenVersionMismatchDoesNotAuthenticate() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    ReflectionTestUtils.setField(user, "id", userId);
    // User의 현재 tokenVersion은 1인데, 토큰엔 예전 버전(0)이 담겨있는 상황
    ReflectionTestUtils.setField(user, "tokenVersion", 1L);
    String token = "valid-token";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    given(jwtProvider.isValid(token)).willReturn(true);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(jwtProvider.getTokenVersion(token)).willReturn(0L);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("잠긴 계정이면 인증되지 않는다")
  void lockedAccountDoesNotAuthenticate() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    User user = User.create("test@otboo.io", "테스트유저", "encoded-password");
    ReflectionTestUtils.setField(user, "id", userId);
    user.lock();

    String token = "valid-token";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();

    given(jwtProvider.isValid(token)).willReturn(true);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(jwtProvider.getTokenVersion(token)).willReturn(user.getTokenVersion());
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    // when
    filter.doFilter(request, response, filterChain);

    // then
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}