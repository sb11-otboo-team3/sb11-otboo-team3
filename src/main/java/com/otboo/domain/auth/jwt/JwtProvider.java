package com.otboo.domain.auth.jwt;

import com.otboo.domain.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
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
  private final long adminAccessExpiration;

  public JwtProvider(JwtProperties jwtProperties) {
    byte[] decodedSecret = Decoders.BASE64.decode(jwtProperties.secret());
    this.key = Keys.hmacShaKeyFor(decodedSecret);
    this.accessExpiration = jwtProperties.accessExpiration();
    this.adminAccessExpiration = jwtProperties.adminAccessExpiration();
  }

  public String createAccessToken(UUID userId, String role, long tokenVersion) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + resolveExpiration(role));

    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_ROLE, role)
        .claim(CLAIM_TOKEN_VERSION, tokenVersion)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key)
        .compact();
  }

  private long resolveExpiration(String role) {
    return UserRole.ADMIN.name().equals(role) ? adminAccessExpiration : accessExpiration;
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
      Claims claims = parseClaims(token);
      return hasValidSubject(claims) && hasValidTokenVersion(claims);
    } catch (ExpiredJwtException e) {
      log.debug("만료된 JWT입니다.", e);
      return false;
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("유효하지 않은 JWT입니다.", e);
      return false;
    }
  }

  private boolean hasValidSubject(Claims claims) {
    String subject = claims.getSubject();
    if (subject == null) {
      log.debug("유효하지 않은 JWT입니다 - subject 없음");
      return false;
    }
    try {
      UUID.fromString(subject);
      return true;
    } catch (IllegalArgumentException e) {
      log.debug("유효하지 않은 JWT입니다 - subject가 UUID 형식이 아님: {}", subject);
      return false;
    }
  }

  private boolean hasValidTokenVersion(Claims claims) {
    Long tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Long.class);
    if (tokenVersion == null) {
      log.debug("유효하지 않은 JWT입니다 - tokenVersion 클레임 없음");
      return false;
    }
    return true;
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith((javax.crypto.SecretKey) key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}