package com.otboo.domain.user.service;

import com.otboo.domain.user.dto.ProfileDto;
import com.otboo.domain.user.entity.Profile;
import com.otboo.domain.user.exception.ProfileNotFoundException;
import com.otboo.domain.user.repository.ProfileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

  private final ProfileRepository profileRepository;

  public ProfileDto getProfile(UUID userId) {
    Profile profile = profileRepository.findById(userId)
        .orElseThrow(() -> new ProfileNotFoundException(userId));

    return ProfileDto.from(profile);
  }
}