package com.otboo.domain.recommendation.service;

import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.recommendation.dto.response.RecommendationClothesResponse;
import
        com.otboo.domain.recommendation.dto.response.RecommendationResponse;
import
        com.otboo.domain.recommendation.exception.LocationNotSetException;
import
        com.otboo.domain.recommendation.exception.WeatherUnavailableException;
import com.otboo.domain.recommendation.llm.LlmOutfitRanker;
import com.otboo.domain.recommendation.llm.dto.OutfitCandidate;
import com.otboo.domain.recommendation.llm.dto.RankedOutfit;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.exception.WeatherNotFoundException;
import com.otboo.domain.weather.service.WeatherService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int DEFAULT_TEMPERATURE_SENSITIVITY = 3;

    private final ProfileRepository profileRepository;
    private final WeatherService weatherService;
    private final RecommendationTransactionalService recommendationTransactionalService;
    private final OutfitRecommendationEngine recommendationEngine;
    private final LlmOutfitRanker llmOutfitRanker;

    // 날씨 API·LLM 블로킹 호출 중에는 DB 트랜잭션을 점유하지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RecommendationResponse recommend(UUID userId, UUID weatherId) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException(userId));

        if (profile.getLatitude() == null || profile.getLongitude() == null) {
            throw new LocationNotSetException(userId);
        }

        List<WeatherDto> weathers;
        try {
            weathers = weatherService.getWeathers(profile.getLatitude(), profile.getLongitude()).block();
        } catch (RuntimeException e) {
            // 날씨 도메인 내부 예외(카카오/기상청 API 실패 등)를 추천 API 계약으로 통일한다.
            throw new WeatherUnavailableException(userId);
        }
        if (weathers == null || weathers.isEmpty()) {
            throw new WeatherUnavailableException(userId);
        }
        WeatherDto today = resolveWeather(weathers, weatherId);

        int temperatureSensitivity = profile.getTemperatureSensitivity() != null
                ? profile.getTemperatureSensitivity()
                : DEFAULT_TEMPERATURE_SENSITIVITY;

        double minTemperature = today.temperature().min();
        double maxTemperature = today.temperature().max();
        PrecipitationType precipitationType = today.precipitation().type();

        List<OutfitCandidate> llmCandidates = recommendationEngine.buildCandidates(
                userId, minTemperature, maxTemperature, precipitationType, temperatureSensitivity
        );
        Optional<List<RankedOutfit>> rankedOutfits = llmOutfitRanker.rank(
                llmCandidates, minTemperature, maxTemperature, precipitationType, temperatureSensitivity
        );

        List<RecommendationClothesResponse> clothes =
                recommendationTransactionalService.recommend(
                        userId, minTemperature, maxTemperature, precipitationType, temperatureSensitivity, rankedOutfits
                );

        return new RecommendationResponse(today.id(), userId, clothes);
    }

    private WeatherDto resolveWeather(List<WeatherDto> weathers, UUID weatherId) {
        return weathers.stream()
                .filter(weather -> weatherId.equals(weather.id()))
                .findFirst()
                .orElseThrow(() -> new WeatherNotFoundException(weatherId));
    }
}
