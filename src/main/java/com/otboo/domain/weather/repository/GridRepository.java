package com.otboo.domain.weather.repository;

import com.otboo.domain.weather.entity.Grid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GridRepository extends JpaRepository<Grid, UUID> {

  Optional<Grid> findByXAndY(int x, int y);

  // 날씨 프리패치 배치가 "실제로 쓰이는 격자"만 골라내는 기준(Grid.lastRequestedAt 참고).
  List<Grid> findByLastRequestedAtAfter(Instant threshold);
}