package com.otboo.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
public class RefreshTokenRedisIntegrationTest {

    private static final String KEY_PREFIX = "refresh:";

    private final Set<String> createdKeys = new HashSet<>();

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        if (!createdKeys.isEmpty()) {
            redisTemplate.delete(createdKeys);
            createdKeys.clear();
        }
    }

    private String issueRefreshToken(UUID userId, long tokenVersion) {
        String refreshToken = refreshTokenService.issue(userId, tokenVersion);
        createdKeys.add(KEY_PREFIX + refreshToken);
        return refreshToken;
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
        long tokenVersion = 2L;

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

    @Test
    @DisplayName("이미 소비된 토큰을 재사용하면 재사용이 감지되고 tokenVersion이 실제로 증가한다")
    void reusedTokenIncrementsTokenVersionInDatabase() {
        // given
        User user = User.create("reuse-integration@otboo.io", "재사용테스트", "encoded-password");
        userRepository.saveAndFlush(user);
        long tokenVersionBefore = user.getTokenVersion();

        String refreshToken = issueRefreshToken(user.getId(), tokenVersionBefore);

        // when: 첫 번째 소비(정상)
        refreshTokenService.consumeTokenInfo(refreshToken);

        // then: 같은 토큰으로 재사용 시도
        Optional<RefreshTokenService.TokenInfo> result =
            refreshTokenService.consumeTokenInfo(refreshToken);

        assertThat(result).isEmpty();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getTokenVersion()).isEqualTo(tokenVersionBefore + 1);
    }
}
