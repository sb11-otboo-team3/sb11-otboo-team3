package com.otboo.domain.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.service.ProfileService;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock
  private ProfileRepository profileRepository;

  @InjectMocks
  private ProfileService profileService;

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
}