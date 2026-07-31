package com.otboo.domain.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.global.config.JpaAuditingConfig;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class GridRepositoryTest {

  @Autowired
  private GridRepository gridRepository;

  @Test
  @DisplayName("저장된 격자로 조회하면 Grid를 반환한다")
  void findsGridByXAndY() {
    // given
    Grid grid = Grid.builder().x(60).y(127).build();
    gridRepository.save(grid);

    // when
    Optional<Grid> found = gridRepository.findByXAndY(60, 127);

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getX()).isEqualTo(60);
    assertThat(found.get().getY()).isEqualTo(127);
  }

  @Test
  @DisplayName("저장되지 않은 격자로 조회하면 빈 Optional을 반환한다")
  void returnsEmptyWhenGridNotFound() {
    // when
    Optional<Grid> found = gridRepository.findByXAndY(60, 127);

    // then
    assertThat(found).isEmpty();
  }
}