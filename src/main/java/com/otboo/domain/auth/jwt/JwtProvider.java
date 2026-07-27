package com.otboo.domain.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtProvider {

  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_TOKEN_VERSION = "tokenVersion";

  private final Key key;
  private final long accessExpiration;

  public JwtProvider(JwtProperties jwtProperties) {
    this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    this.accessExpiration = jwtProperties.accessExpiration();
  }

  public String createAccessToken(UUID userId, String role, long tokenVersion) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + accessExpiration);

    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_ROLE, role)
        .claim(CLAIM_TOKEN_VERSION, tokenVersion)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key)
        .compact();
  }

  public UUID getUserId(String token) {
    return UUID.fromString(parseClaims(token).getSubject());
  }

  public String getRole(String token) {
    return parseClaims(token).get(CLAIM_ROLE, String.class);
  }

  public long getTokenVersion(String token) {
    return parseClaims(token).get(CLAIM_TOKEN_VERSION, Long.class);
  }

  public boolean isValid(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.debug("만료된 JWT입니다.", e);
      return false;
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("유효하지 않은 JWT입니다.", e);
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith((javax.crypto.SecretKey) key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}