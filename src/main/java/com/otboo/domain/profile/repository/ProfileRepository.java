package com.otboo.domain.profile.repository;

import com.otboo.domain.profile.entity.Profile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

  Optional<Profile> findByUserId(UUID userId);

  // 날씨 급변 대상 grid에 사는 유저를 역조회하기 위한 파생 쿼리
  List<Profile> findByXAndY(Integer x, Integer y);
}