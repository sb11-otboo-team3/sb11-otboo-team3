package com.otboo.domain.weather.repository;

import com.otboo.domain.weather.entity.Location;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {

  Optional<Location> findByXAndY(int x, int y);
}