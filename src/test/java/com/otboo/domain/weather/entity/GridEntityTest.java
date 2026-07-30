package com.otboo.domain.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class GridEntityTest {

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("같은 격자(x, y)는 중복 저장할 수 없다")
  void throwsExceptionWhenDuplicateGridIsSaved() {
    // given
    Grid first = Grid.builder().x(60).y(127).build();
    entityManager.persist(first);
    entityManager.flush();

    Grid duplicate = Grid.builder().x(60).y(127).build();

    // when & then
    assertThatThrownBy(() -> {
      entityManager.persist(duplicate);
      entityManager.flush();
    }).isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("builder로 생성하면 필드가 그대로 채워진다")
  void fillsFieldsWhenBuilt() {
    // given & when
    Grid grid = Grid.builder().x(60).y(127).build();

    // then
    assertThat(grid.getX()).isEqualTo(60);
    assertThat(grid.getY()).isEqualTo(127);
  }

  @Test
  @DisplayName("builder로 생성하면 최근 요청 시각도 함께 기록된다")
  void recordsLastRequestedAtWhenBuilt() {
    // given & when
    Grid grid = Grid.builder().x(60).y(127).build();

    // then
    assertThat(grid.getLastRequestedAt()).isNotNull();
  }

  @Test
  @DisplayName("refreshRequestedAt 호출 시 최근 요청 시각이 갱신된다")
  void updatesLastRequestedAtWhenRefreshed() throws InterruptedException {
    // given
    Grid grid = Grid.builder().x(60).y(127).build();
    Instant before = grid.getLastRequestedAt();
    Thread.sleep(5);

    // when
    grid.refreshRequestedAt();

    // then
    assertThat(grid.getLastRequestedAt()).isAfter(before);
  }
}