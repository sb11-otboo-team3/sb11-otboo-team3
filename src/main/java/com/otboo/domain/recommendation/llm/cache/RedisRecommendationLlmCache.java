package com.otboo.domain.recommendation.llm.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.recommendation.llm.dto.RankedOutfit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRecommendationLlmCache implements RecommendationLlmCache {

    private static final String KEY_PREFIX = "recommendation:llm:";
    private static final String DELIMITER = ":";
    private static final Duration TTL = Duration.ofHours(2);
    private static final TypeReference<List<RankedOutfit>> RANKED_OUTFIT_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<List<RankedOutfit>> find(UUID userId, Instant forecastAt) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(key(userId, forecastAt));
        } catch (DataAccessException e) {
            log.warn("추천 LLM 캐시 조회 실패 - Redis 접근 불가, 캐시 미스로 처리, userId={}, forecastAt={}", userId, forecastAt, e);
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RANKED_OUTFIT_LIST_TYPE));
        } catch (JsonProcessingException e) {
            log.warn("추천 LLM 캐시 역직렬화 실패 - 캐시 미스로 처리, userId={}, forecastAt={}", userId, forecastAt, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(UUID userId, Instant forecastAt, List<RankedOutfit> rankedOutfits) {
        String json;
        try {
            json = objectMapper.writeValueAsString(rankedOutfits);
        } catch (JsonProcessingException e) {
            log.warn("추천 LLM 캐시 직렬화 실패 - 캐시 쓰기 생략, userId={}, forecastAt={}", userId, forecastAt, e);
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(userId, forecastAt), json, TTL);
        } catch (DataAccessException e) {
            log.warn("추천 LLM 캐시 저장 실패 - Redis 접근 불가, 캐시 쓰기 생략, userId={}, forecastAt={}", userId, forecastAt, e);
        }
    }

    private String key(UUID userId, Instant forecastAt) {
        return KEY_PREFIX + userId + DELIMITER + forecastAt.getEpochSecond();
    }
}
