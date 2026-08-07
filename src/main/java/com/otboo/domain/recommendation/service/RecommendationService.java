package com.otboo.domain.recommendation.service;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import
        com.otboo.domain.recommendation.dto.response.RecommendationResponse;
import
        com.otboo.domain.recommendation.exception.LocationNotSetException;
import
        com.otboo.domain.recommendation.exception.WeatherUnavailableException;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.service.WeatherService;

import java.util.List;
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
    private final RecommendationTransactionalService
            recommendationTransactionalService;

    // 날씨 API 블로킹 호출 중에는 DB 트랜잭션을 점유하지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RecommendationResponse recommend(UUID userId) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException(userId));

        if (profile.getLatitude() == null || profile.getLongitude() == null) {
            throw new LocationNotSetException(userId);
        }

        List<WeatherDto> weathers = weatherService.getWeathers(profile.getLatitude(), profile.getLongitude()).block();
        if (weathers == null || weathers.isEmpty()) {
            throw new WeatherUnavailableException(userId);
        }
        WeatherDto today = weathers.get(0);

        int temperatureSensitivity = profile.getTemperatureSensitivity() != null
                ? profile.getTemperatureSensitivity()
                : DEFAULT_TEMPERATURE_SENSITIVITY;

        List<ClothesResponse> clothes =
                recommendationTransactionalService.recommend(
                        userId,
                        today.temperature().min(),
                        today.temperature().max(),
                        today.precipitation().type(),
                        temperatureSensitivity
                );

        return new RecommendationResponse(today.id(), clothes);
    }
}
