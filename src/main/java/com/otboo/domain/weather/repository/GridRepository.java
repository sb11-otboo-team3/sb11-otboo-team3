package com.otboo.domain.weather.repository;

import com.otboo.domain.weather.entity.Grid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GridRepository extends JpaRepository<Grid, UUID> {

  Optional<Grid> findByXAndY(int x, int y);
}