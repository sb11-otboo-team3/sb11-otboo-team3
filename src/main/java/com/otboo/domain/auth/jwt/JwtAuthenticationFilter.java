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