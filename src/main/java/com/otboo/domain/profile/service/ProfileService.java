package com.otboo.domain.profile.service;

import com.otboo.domain.profile.dto.ProfileDto;
import com.otboo.domain.profile.dto.ProfileUpdateRequest;
import com.otboo.domain.profile.entity.Profile;
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

  // 위치 조회(카카오 API 호출 포함, block())를 트랜잭션 밖에서 먼저 끝내고, DB 저장은
  // ProfileUpdateTransactionalService의 별도 트랜잭션에 맡긴다. 이렇게 안 하면 카카오 API
  // 응답을 기다리는 동안 DB 커넥션과 트랜잭션이 계속 열려있게 된다 - Tomcat 스레드보다
  // 훨씬 적은 DB 커넥션 풀을 그만큼 오래 붙잡아두는 셈이라 더 빨리 고갈될 수 있다.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ProfileDto updateProfile(UUID userId, ProfileUpdateRequest request) {
    WeatherAPILocation location = null;

    if (request.location() != null
        && request.location().latitude() != null
        && request.location().longitude() != null) {
      location = locationResolver.resolve(
          request.location().latitude(), request.location().longitude()
      ).block();
    }

    return profileUpdateTransactionalService.update(userId, request, location);
  }
}