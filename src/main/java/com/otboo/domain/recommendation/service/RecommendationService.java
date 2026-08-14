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
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.exception.WeatherNotFoundException;
import com.otboo.domain.weather.service.WeatherService;

import java.util.List;
import java.util.Set;
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

    // 날씨 API 블로킹 호출 중에는 DB 트랜잭션을 점유하지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RecommendationResponse recommend(UUID userId, Set<UUID> excludeClothesIds, UUID weatherId) {
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

        List<RecommendationClothesResponse> clothes =
                recommendationTransactionalService.recommend(
                        userId,
                        today.temperature().min(),
                        today.temperature().max(),
                        today.precipitation().type(),
                        temperatureSensitivity,
                        excludeClothesIds
                );

        return new RecommendationResponse(today.id(), clothes);
    }

    private WeatherDto resolveWeather(List<WeatherDto> weathers, UUID weatherId) {
        if (weatherId == null) {
            return weathers.get(0);
        }
        return weathers.stream()
                .filter(weather -> weatherId.equals(weather.id()))
                .findFirst()
                .orElseThrow(() -> new WeatherNotFoundException(weatherId));
    }
}
