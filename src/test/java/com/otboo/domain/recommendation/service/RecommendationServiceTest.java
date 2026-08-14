package com.otboo.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.ClothesType;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.recommendation.dto.response.RecommendationResponse;
import com.otboo.domain.recommendation.exception.LocationNotSetException;
import com.otboo.domain.recommendation.exception.WeatherUnavailableException;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.service.WeatherService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private WeatherService weatherService;

    @Mock
    private RecommendationTransactionalService recommendationTransactionalService;

    @InjectMocks
    private RecommendationService service;

    private final User owner = User.create("test@otboo.io", "테스트", "encoded-password");

    @Test
    void 프로필이_없으면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        given(profileRepository.findById(userId)).willReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> service.recommend(userId, Set.of()))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void 위치가_설정되지_않으면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        Profile profile = Profile.createDefault(owner);
        ReflectionTestUtils.setField(profile, "userId", userId);

        given(profileRepository.findById(userId)).willReturn(Optional.of(profile));

        //when & then
        assertThatThrownBy(() -> service.recommend(userId, Set.of()))
                .isInstanceOf(LocationNotSetException.class);
    }

    @Test
    void 날씨_데이터가_없으면_예외가_발생한다() {
        //given
        UUID userId = UUID.randomUUID();
        Profile profile = Profile.createDefault(owner);
        ReflectionTestUtils.setField(profile, "userId", userId);
        ReflectionTestUtils.setField(profile, "latitude", 37.5);
        ReflectionTestUtils.setField(profile, "longitude", 127.0);

        given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
        given(weatherService.getWeathers(37.5, 127.0)).willReturn(Mono.just(List.of()));

        //when & then
        assertThatThrownBy(() -> service.recommend(userId, Set.of()))
                .isInstanceOf(WeatherUnavailableException.class);
    }

    @Test
    void 날씨_API_호출이_실패하면_WeatherUnavailableException으로_변환된다() {
        //given
        UUID userId = UUID.randomUUID();
        Profile profile = Profile.createDefault(owner);
        ReflectionTestUtils.setField(profile, "userId", userId);
        ReflectionTestUtils.setField(profile, "latitude", 37.5);
        ReflectionTestUtils.setField(profile, "longitude", 127.0);

        given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
        given(weatherService.getWeathers(37.5, 127.0))
                .willReturn(Mono.error(new RuntimeException("기상청 API 호출 실패")));

        //when & then
        assertThatThrownBy(() -> service.recommend(userId, Set.of()))
                .isInstanceOf(WeatherUnavailableException.class);
    }

    @Test
    void 온도민감도가_null이면_기본값_3이_적용된다() {
        //given
        UUID userId = UUID.randomUUID();
        Profile profile = Profile.createDefault(owner);
        ReflectionTestUtils.setField(profile, "userId", userId);
        ReflectionTestUtils.setField(profile, "latitude", 37.5);
        ReflectionTestUtils.setField(profile, "longitude", 127.0);
        // temperatureSensitivity는 세팅하지 않아 null로 남는다

        WeatherDto weatherDto = weatherDto(UUID.randomUUID(), 5.0, 10.0, PrecipitationType.NONE);

        given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
        given(weatherService.getWeathers(37.5, 127.0)).willReturn(Mono.just(List.of(weatherDto)));
        given(recommendationTransactionalService.recommend(userId, 5.0, 10.0, PrecipitationType.NONE, 3, Set.of()))
                .willReturn(List.of());

        //when
        service.recommend(userId, Set.of());

        //then
        verify(recommendationTransactionalService)
                .recommend(userId, 5.0, 10.0, PrecipitationType.NONE, 3, Set.of());
    }

    @Test
    void 정상_흐름이면_추천_결과를_반환한다() {
        //given
        UUID userId = UUID.randomUUID();
        Profile profile = Profile.createDefault(owner);
        ReflectionTestUtils.setField(profile, "userId", userId);
        ReflectionTestUtils.setField(profile, "latitude", 37.5);
        ReflectionTestUtils.setField(profile, "longitude", 127.0);
        ReflectionTestUtils.setField(profile, "temperatureSensitivity", 4);

        UUID weatherId = UUID.randomUUID();
        WeatherDto weatherDto = weatherDto(weatherId, 5.0, 10.0, PrecipitationType.RAIN);

        ClothesResponse clothesResponse = new ClothesResponse(
                UUID.randomUUID(), userId, "코트", null, ClothesType.OUTER, List.of()
        );

        given(profileRepository.findById(userId)).willReturn(Optional.of(profile));
        given(weatherService.getWeathers(37.5, 127.0)).willReturn(Mono.just(List.of(weatherDto)));
        given(recommendationTransactionalService.recommend(userId, 5.0, 10.0, PrecipitationType.RAIN, 4, Set.of()))
                .willReturn(List.of(clothesResponse));

        //when
        RecommendationResponse response = service.recommend(userId, Set.of());

        //then
        assertThat(response.weatherId()).isEqualTo(weatherId);
        assertThat(response.clothes()).containsExactly(clothesResponse);
    }

    private WeatherDto weatherDto(UUID id, double min, double max, PrecipitationType precipitationType) {
        return new WeatherDto(
                id,
                null,
                null,
                null,
                null,
                new PrecipitationDto(precipitationType, 0.0, 0.0),
                null,
                new TemperatureDto(0.0, 0.0, min, max),
                null
        );
    }
}