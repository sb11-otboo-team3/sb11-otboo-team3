package com.otboo.domain.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Gender;
import com.otboo.domain.profile.service.ProfileService;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.service.LocationResolver;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private LocationResolver locationResolver;

  private ProfileService profileService;

  // ProfileUpdateTransactionalService는 목이 아니라 실제 인스턴스를 씀 - ProfileService는
  // 위치 조회 오케스트레이션만 하고, 실제 DB 반영/DTO 변환은 이 클래스가 하므로 그래야
  // updateProfile 관련 테스트들이 기존처럼 결과값(location, name 등)을 그대로 검증할 수 있다.
  @BeforeEach
  void setUp() {
    ProfileUpdateTransactionalService profileUpdateTransactionalService =
        new ProfileUpdateTransactionalService(profileRepository);
    profileService = new ProfileService(profileRepository, locationResolver, profileUpdateTransactionalService);
  }

  @Test
  @DisplayName("프로필을 조회하면 ProfileDto를 반환한다")
  void getProfileReturnsProfileDto() throws Exception {
    // given
    User user = User.create("profiletest@otboo.io", "프로필테스트", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));

    // when
    ProfileDto result = profileService.getProfile(userId);

    // then
    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.name()).isEqualTo("프로필테스트");
    // 위치를 한 번도 설정한 적 없는 프로필은 location 자체가 null이어야 한다(필드만 null인 빈 객체 X).
    assertThat(result.location()).isNull();
  }

  @Test
  @DisplayName("존재하지 않는 프로필을 조회하면 예외가 발생한다")
  void getProfileWithNonExistentProfileThrowsException() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(profileRepository.findById(userId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> profileService.getProfile(userId))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  @DisplayName("프로필을 수정하면 갱신된 ProfileDto를 반환한다")
  void updateProfileReturnsUpdatedProfileDto() throws Exception {
    // given
    User user = User.create("updatetest@otboo.io", "기존이름", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));

    ProfileUpdateRequest.LocationUpdateRequest location =
        new ProfileUpdateRequest.LocationUpdateRequest(37.5, 127.0);
    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "새이름", Gender.MALE, LocalDate.of(1995, 5, 5), location, 3
    );

    WeatherAPILocation weatherLocation = new WeatherAPILocation(
        37.5, 127.0, 60, 127, List.of("서울특별시", "강남구", "역삼동")
    );
    given(locationResolver.resolve(37.5, 127.0)).willReturn(Mono.just(weatherLocation));

    // when
    ProfileDto result = profileService.updateProfile(userId, request);

    // then
    assertThat(result.name()).isEqualTo("새이름");
    assertThat(result.gender()).isEqualTo(Gender.MALE);
    assertThat(result.location().x()).isEqualTo(60);
    assertThat(result.location().locationNames()).containsExactly("서울특별시", "강남구", "역삼동");
    assertThat(result.temperatureSensitivity()).isEqualTo(3);
  }

  @Test
  @DisplayName("위치 정보 없이 수정하면 기존 위치 정보가 유지된다")
  void updateProfileWithoutLocationKeepsExistingLocation() throws Exception {
    // given
    User user = User.create("keeplocation@otboo.io", "기존이름", "encoded-password");
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);
    profile.update(
        Gender.FEMALE, LocalDate.of(1990, 1, 1),
        37.0, 126.0, 50, 100,
        "서울특별시", "종로구", "청운동", 2
    );

    given(profileRepository.findById(userId)).willReturn(Optional.of(profile));

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        null, null, null, null, 4
    );

    // when
    ProfileDto result = profileService.updateProfile(userId, request);

    // then
    assertThat(result.temperatureSensitivity()).isEqualTo(4);
    assertThat(result.location().x()).isEqualTo(50);
    assertThat(result.location().locationNames()).containsExactly("서울특별시", "종로구", "청운동");
  }

  @Test
  @DisplayName("존재하지 않는 프로필을 수정하려 하면 예외가 발생한다")
  void updateProfileWithNonExistentProfileThrowsException() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(profileRepository.findById(userId)).willReturn(Optional.empty());

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "이름", null, null, null, null
    );

    // when & then
    assertThatThrownBy(() -> profileService.updateProfile(userId, request))
        .isInstanceOf(ProfileNotFoundException.class);
  }
}