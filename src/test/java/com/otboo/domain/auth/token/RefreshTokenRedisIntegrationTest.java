package com.otboo.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
public class RefreshTokenRedisIntegrationTest {

    private static final String KEY_PREFIX = "refresh:";

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(redisTemplate.keys(KEY_PREFIX + "*"));
    }

    @Test
    @DisplayName("Refresh Token 발급 시 실제 Redis에 저장된다")
    void issuStoresRefreshTokenInRedis() {
        // given
        UUID userId = UUID.randomUUID();
        long tokenVersion = 1L;

        // when
        String refreshToken = refreshTokenService.issue(userId, tokenVersion);

        // then
        String storedValue = redisTemplate.opsForValue()
                .get(KEY_PREFIX + refreshToken);

        assertThat(storedValue).isEqualTo(userId + ":" + tokenVersion);
    }

    @Test
    @DisplayName("Refresh Token을 소비하면 정보를 반환하고 Redis에서 삭제된다")
    void consumeTokenInfoReturnsInfoAndDeletesTokenFromRedis() {
        // given
        UUID userId = UUID.randomUUID();
        long tokenVersion = 1L;

        String refreshToken = refreshTokenService.issue(userId, tokenVersion);

        // when
        Optional<RefreshTokenService.TokenInfo> result =
                refreshTokenService.consumeTokenInfo(refreshToken);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(userId);
        assertThat(result.get().tokenVersion()).isEqualTo(tokenVersion);

        assertThat(
                redisTemplate.hasKey(KEY_PREFIX + refreshToken)
        ).isFalse();
    }
}
