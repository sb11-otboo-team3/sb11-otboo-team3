package com.otboo.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties(
        "3WayDafV59YynmTwCpaDtnpeur8sokrAkQ+wFlXO4QY=",
        900000L,
        604800000L
    );
    jwtProvider = new JwtProvider(properties);
  }

  @Test
  @DisplayName("토큰 발급 후 userId, role, tokenVersion을 정확히 추출한다")
  void extractClaimsFromCreatedToken() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when
    String token = jwtProvider.createAccessToken(userId, "USER", 3L);

    // then
    assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
    assertThat(jwtProvider.getRole(token)).isEqualTo("USER");
    assertThat(jwtProvider.getTokenVersion(token)).isEqualTo(3L);
  }

  @Test
  @DisplayName("정상적으로 발급된 토큰은 유효하다")
  void validTokenIsValid() throws Exception {
    // given
    String token = jwtProvider.createAccessToken(UUID.randomUUID(), "USER", 0L);

    // when & then
    assertThat(jwtProvider.isValid(token)).isTrue();
  }

  @Test
  @DisplayName("잘못된 형식의 토큰은 유효하지 않다")
  void invalidFormatTokenIsNotValid() throws Exception {
    // given
    String invalidToken = "this-is-not-a-valid-jwt";

    // when & then
    assertThat(jwtProvider.isValid(invalidToken)).isFalse();
  }

  @Test
  @DisplayName("tokenVersion 클레임이 없는 토큰은 유효하지 않다")
  void tokenWithoutTokenVersionClaimIsNotValid() throws Exception {
    // given
    byte[] decodedSecret = Decoders.BASE64.decode("3WayDafV59YynmTwCpaDtnpeur8sokrAkQ+wFlXO4QY=");
    Key testKey = Keys.hmacShaKeyFor(decodedSecret);

    String tokenWithoutTokenVersion = Jwts.builder()
        .subject(UUID.randomUUID().toString())
        .claim("role", "USER")
        // tokenVersion 클레임 의도적으로 누락
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 900000))
        .signWith(testKey)
        .compact();

    // when & then
    assertThat(jwtProvider.isValid(tokenWithoutTokenVersion)).isFalse();
  }

  @Test
  @DisplayName("subject가 UUID 형식이 아닌 토큰은 유효하지 않다")
  void tokenWithNonUuidSubjectIsNotValid() throws Exception {
    // given
    byte[] decodedSecret = Decoders.BASE64.decode("3WayDafV59YynmTwCpaDtnpeur8sokrAkQ+wFlXO4QY=");
    Key testKey = Keys.hmacShaKeyFor(decodedSecret);

    String tokenWithInvalidSubject = Jwts.builder()
        .subject("not-a-uuid")
        .claim("role", "USER")
        .claim("tokenVersion", 0L)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 900000))
        .signWith(testKey)
        .compact();

    // when & then
    assertThat(jwtProvider.isValid(tokenWithInvalidSubject)).isFalse();
  }
}