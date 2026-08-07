package com.otboo.domain.profile.service;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.exception.LocationResolutionFailedException;
import com.otboo.domain.profile.exception.ProfileNotFoundException;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.service.LocationResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

  private final ProfileRepository profileRepository;
  private final LocationResolver locationResolver;
  private final ProfileUpdateTransactionalService profileUpdateTransactionalService;

  public ProfileDto getProfile(UUID userId) {
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    return ProfileDto.from(profile);
  }

  // 업데이트(DB)를 따로 빈으로 분리
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ProfileDto updateProfile(UUID userId, ProfileUpdateRequest request) {
    WeatherAPILocation location = null;

    if (request.location() != null
        && request.location().latitude() != null
        && request.location().longitude() != null) {
      double latitude = request.location().latitude();
      double longitude = request.location().longitude();
      location = locationResolver.resolve(latitude, longitude).block();



      if (location == null) {
        throw new LocationResolutionFailedException(latitude, longitude);
      }
    }

    return profileUpdateTransactionalService.update(userId, request, location);
  }
}