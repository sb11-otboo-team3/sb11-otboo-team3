package com.otboo.domain.auth.jwt;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtProvider jwtProvider;
  private final UserRepository userRepository;

  // OncePerRequestFilter는 기본적으로 ASYNC 디스패치(Mono/DeferredResult 응답 완료 시점)엔 다시 안 돈다.
  // 이 필터가 REQUEST 디스패치에서만 인증을 세팅하면, 이 앱은 STATELESS라 세션 등 다른 곳에
  // SecurityContext를 복구할 수단이 없어서 ASYNC 디스패치 시점엔 인증 정보가 사라진다.
  // 그 상태로 AuthorizationFilter가 다시 평가되면 미인증으로 401이 나버리므로, ASYNC에서도 이 필터가
  // 다시 돌게 해서 매번 토큰으로 인증을 재구성해야 한다.
  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {

    String token = resolveToken(request);

    if (StringUtils.hasText(token) && jwtProvider.isValid(token)) {
      authenticate(token);
    }

    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION_HEADER);
    if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  private void authenticate(String token) {
    UUID userId = jwtProvider.getUserId(token);
    long tokenVersion = jwtProvider.getTokenVersion(token);

    Optional<User> userOptional = userRepository.findById(userId);
    if (userOptional.isEmpty()) {
      log.debug("인증 실패 - 존재하지 않는 사용자: {}", userId);
      return;
    }

    User user = userOptional.get();

    if (user.getTokenVersion() != tokenVersion) {
      log.debug("인증 실패 - tokenVersion 불일치 (토큰 무효화됨): {}", userId);
      return;
    }

    if (user.isLocked()) {
      log.debug("인증 실패 - 잠긴 계정: {}", userId);
      return;
    }

    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        user.getId(),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
    );

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}