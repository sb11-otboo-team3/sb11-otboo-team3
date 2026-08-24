package com.otboo.domain.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Gender;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.LocationResolutionFailedException;
import com.otboo.domain.profile.exception.ProfileAccessDeniedException;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.service.LocationResolver;
import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.StorageDirectory;
import com.otboo.global.infrastructure.storage.StoredFile;
import com.otboo.global.infrastructure.storage.event.FileDeletionRetryService;
import com.otboo.global.infrastructure.storage.event.FileReplacementEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private LocationResolver locationResolver;

  @Mock
  private FileStorage fileStorage;

  private ProfileService profileService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private FileDeletionRetryService fileDeletionRetryService;

  @BeforeEach
  void setUp() {
    ProfileUpdateTransactionalService profileUpdateTransactionalService =
        new ProfileUpdateTransactionalService(profileRepository, eventPublisher);
    profileService = new ProfileService(
        profileRepository,
        locationResolver,
        fileStorage,
        profileUpdateTransactionalService,
        fileDeletionRetryService
    );
  }

  @Test
  @DisplayName("프로필을 조회하면 ProfileDto를 반환한다")
  void getProfileReturnsProfileDto() {
    // given
    User user = User.create(
        "profiletest@otboo.io",
        "프로필테스트",
        "encoded-password"
    );

    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile));

    // when
    ProfileDto result = profileService.getProfile(userId, userId);

    // then
    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.name()).isEqualTo("프로필테스트");
    assertThat(result.location()).isNull();
  }

  @Test
  @DisplayName("존재하지 않는 프로필을 조회하면 예외가 발생한다")
  void getProfileWithNonExistentProfileThrowsException() {
    // given
    UUID userId = UUID.randomUUID();

    given(profileRepository.findById(userId))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> profileService.getProfile(userId, userId))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  @DisplayName("본인이 아닌 사용자의 프로필을 수정하면 예외가 발생한다")
  void updateProfileWithDifferentUserThrowsException() {
    // given
    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "이름", null, null, null, null
    );

    // when & then
    assertThatThrownBy(
        () -> profileService.updateProfile(userId, otherUserId, request, null)
    ).isInstanceOf(ProfileAccessDeniedException.class);
  }

  @Test
  @DisplayName("프로필을 수정하면 갱신된 ProfileDto를 반환한다")
  void updateProfileReturnsUpdatedProfileDto() {
    // given
    User user = User.create(
        "updatetest@otboo.io",
        "기존이름",
        "encoded-password"
    );

    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile));

    ProfileUpdateRequest.LocationUpdateRequest location =
        new ProfileUpdateRequest.LocationUpdateRequest(37.5, 127.0);

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "새이름",
        Gender.MALE,
        LocalDate.of(1995, 5, 5),
        location,
        3
    );

    WeatherAPILocation weatherLocation = new WeatherAPILocation(
        37.5,
        127.0,
        60,
        127,
        List.of("서울특별시", "강남구", "역삼동")
    );

    given(locationResolver.resolve(37.5, 127.0))
        .willReturn(Mono.just(weatherLocation));

    // when
    ProfileDto result =
        profileService.updateProfile(userId, userId, request, null);

    // then
    assertThat(result.name()).isEqualTo("새이름");
    assertThat(result.gender()).isEqualTo(Gender.MALE);
    assertThat(result.location().x()).isEqualTo(60);
    assertThat(result.location().locationNames())
        .containsExactly("서울특별시", "강남구", "역삼동");
    assertThat(result.temperatureSensitivity()).isEqualTo(3);
  }

  @Test
  @DisplayName("세종시처럼 행정구역 단계가 3개 미만이어도 있는 만큼만 반영한다")
  void updateProfileHandlesLocationNamesWithFewerThanThreeElements() {
    // given
    User user = User.create(
        "sejong@otboo.io",
        "기존이름",
        "encoded-password"
    );

    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile));

    ProfileUpdateRequest.LocationUpdateRequest location =
        new ProfileUpdateRequest.LocationUpdateRequest(36.48, 127.29);

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        null,
        null,
        null,
        location,
        null
    );

    WeatherAPILocation weatherLocation = new WeatherAPILocation(
        36.48,
        127.29,
        70,
        80,
        List.of("세종특별자치시", "종촌동")
    );

    given(locationResolver.resolve(36.48, 127.29))
        .willReturn(Mono.just(weatherLocation));

    // when
    ProfileDto result =
        profileService.updateProfile(userId, userId, request, null);

    // then
    assertThat(result.location().locationNames())
        .containsExactly("세종특별자치시", "종촌동");
  }

  @Test
  @DisplayName("위치 정보 없이 수정하면 기존 위치 정보가 유지된다")
  void updateProfileWithoutLocationKeepsExistingLocation() {
    // given
    User user = User.create(
        "keeplocation@otboo.io",
        "기존이름",
        "encoded-password"
    );

    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    profile.update(
        Gender.FEMALE,
        LocalDate.of(1990, 1, 1),
        37.0,
        126.0,
        50,
        100,
        "서울특별시",
        "종로구",
        "청운동",
        2
    );

    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile));

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        null,
        null,
        null,
        null,
        4
    );

    // when
    ProfileDto result =
        profileService.updateProfile(userId, userId, request, null);

    // then
    assertThat(result.temperatureSensitivity()).isEqualTo(4);
    assertThat(result.location().x()).isEqualTo(50);
    assertThat(result.location().locationNames())
        .containsExactly("서울특별시", "종로구", "청운동");
  }

  @Test
  @DisplayName("위치 조회가 빈 신호로 끝나면 예외를 던진다")
  void updateProfileThrowsWhenLocationResolutionCompletesEmpty() {
    // given
    UUID userId = UUID.randomUUID();

    User user = User.create(
        "location-empty@otboo.io",
        "기존이름",
        "encoded-password"
    );

    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile));

    ProfileUpdateRequest.LocationUpdateRequest location =
        new ProfileUpdateRequest.LocationUpdateRequest(37.5, 127.0);

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "새이름",
        Gender.MALE,
        LocalDate.of(1995, 5, 5),
        location,
        3
    );

    given(locationResolver.resolve(37.5, 127.0))
        .willReturn(Mono.empty());

    // when & then
    assertThatThrownBy(
        () -> profileService.updateProfile(userId, userId, request, null)
    ).isInstanceOf(LocationResolutionFailedException.class);
  }

  @Test
  @DisplayName("존재하지 않는 프로필을 수정하려 하면 예외가 발생한다")
  void updateProfileWithNonExistentProfileThrowsException() {
    // given
    UUID userId = UUID.randomUUID();

    given(profileRepository.findById(userId))
        .willReturn(Optional.empty());

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        "이름",
        null,
        null,
        null,
        null
    );

    // when & then
    assertThatThrownBy(
        () -> profileService.updateProfile(userId, userId, request, null)
    ).isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  @DisplayName("본인이 아닌 사용자도 프로필을 정상적으로 조회할 수 있다")
  void getProfileWithDifferentUserReturnsProfileDto() {
    // given
    User user = User.create(
        "othersprofile@otboo.io",
        "다른유저프로필",
        "encoded-password"
    );
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    UUID otherUserId = UUID.randomUUID();

    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile));

    // when
    ProfileDto result = profileService.getProfile(userId, otherUserId);

    // then
    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.name()).isEqualTo("다른유저프로필");
  }

  @Test
  @DisplayName("이미지와 함께 프로필을 수정하면 새 Object Key로 갱신되고 기존 이미지는 삭제된다")
  void updateProfileWithImageUploadsAndDeletesOldImage() {
    // given
    User user = User.create(
        "imagetest@otboo.io",
        "이미지테스트",
        "encoded-password"
    );
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);
    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);
    ReflectionTestUtils.setField(
        profile,
        "imageKey",
        "profiles/" + userId + "/old-key.png"
    );
    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile));
    ProfileUpdateRequest request = new ProfileUpdateRequest(
        null,
        null,
        null,
        null,
        null
    );
    MultipartFile image = new MockMultipartFile(
        "image",
        "test.png",
        "image/png",
        "dummy-content".getBytes()
    );
    StoredFile storedFile = new StoredFile(
        "profiles/" + userId + "/new-key.png",
        "image/png",
        13L,
        null
    );
    given(
        fileStorage.uploadWithThumbnail(
            StorageDirectory.PROFILES,
            userId,
            image
        )
    ).willReturn(storedFile);
    given(
        fileStorage.generateReadUrl(
            "profiles/" + userId + "/new-key.png"
        )
    ).willReturn("https://example.com/new-key.png");
    // when
    ProfileDto result =
        profileService.updateProfile(userId, userId, request, image);
    // then
    assertThat(result.profileImageUrl())
        .isEqualTo("https://example.com/new-key.png");
    assertThat(profile.getImageKey())
        .isEqualTo("profiles/" + userId + "/new-key.png");
    verify(fileStorage).uploadWithThumbnail(
        StorageDirectory.PROFILES,
        userId,
        image
    );
    verify(eventPublisher).publishEvent(
        new FileReplacementEvent(
            "profiles/" + userId + "/old-key.png",
            "profiles/" + userId + "/new-key.png"
        )
    );
  }

  @Test
  @DisplayName("기존 이미지가 없는 상태에서 업로드하면 삭제를 호출하지 않는다")
  void updateProfileWithImageAndNoExistingImageDoesNotCallDelete() {
    // given
    User user = User.create(
        "firstimage@otboo.io",
        "첫이미지",
        "encoded-password"
    );

    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile));

    ProfileUpdateRequest request = new ProfileUpdateRequest(
        null,
        null,
        null,
        null,
        null
    );

    MultipartFile image = new MockMultipartFile(
        "image",
        "test.png",
        "image/png",
        "dummy-content".getBytes()
    );

    StoredFile storedFile = new StoredFile(
        "profiles/" + userId + "/first-key.png",
        "image/png",
        13L,
        null
    );

    given(
        fileStorage.uploadWithThumbnail(
            StorageDirectory.PROFILES,
            userId,
            image
        )
    ).willReturn(storedFile);

    given(
        fileStorage.generateReadUrl(
            "profiles/" + userId + "/first-key.png"
        )
    ).willReturn("https://example.com/first-key.png");

    // when
    ProfileDto result =
        profileService.updateProfile(userId, userId, request, image);

    // then
    assertThat(result.profileImageUrl())
        .isEqualTo("https://example.com/first-key.png");

    assertThat(profile.getImageKey())
        .isEqualTo("profiles/" + userId + "/first-key.png");

    verify(fileStorage).uploadWithThumbnail(
        StorageDirectory.PROFILES,
        userId,
        image
    );
    verify(eventPublisher).publishEvent(
        new FileReplacementEvent(
            null,
            "profiles/" + userId + "/first-key.png"
        )
    );
  }

  @Test
  @DisplayName("이미지 업로드 후 프로필 갱신이 실패하면 새로 업로드한 이미지를 정리한다")
  void updateProfileCleansUpNewImageWhenUpdateFails() {
    // given
    User user = User.create(
        "cleanup@otboo.io",
        "정리테스트",
        "encoded-password"
    );
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    Profile profile = Profile.createDefault(user);
    ReflectionTestUtils.setField(profile, "userId", userId);

    // 첫 번째 조회(ProfileService)는 성공, 두 번째 조회(ProfileUpdateTransactionalService)는
    // 동시 삭제 등으로 실패하는 상황을 가정
    given(profileRepository.findById(userId))
        .willReturn(Optional.of(profile))
        .willReturn(Optional.empty());

    ProfileUpdateRequest request = new ProfileUpdateRequest(null, null, null, null, null);
    MultipartFile image = new MockMultipartFile(
        "image", "test.png", "image/png", "dummy-content".getBytes()
    );

    StoredFile storedFile = new StoredFile(
        "profiles/" + userId + "/orphan-key.png",
        "image/png",
        13L,
        null
    );
    given(
        fileStorage.uploadWithThumbnail(StorageDirectory.PROFILES, userId, image)
    ).willReturn(storedFile);

    // when & then
    assertThatThrownBy(() -> profileService.updateProfile(userId, userId, request, image))
        .isInstanceOf(ProfileNotFoundException.class);

    verify(fileDeletionRetryService).deleteWithRetry(
        "profiles/" + userId + "/orphan-key.png"
    );
  }
}