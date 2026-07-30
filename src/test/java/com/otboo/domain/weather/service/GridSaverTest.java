package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.repository.GridRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GridSaverTest {

  @Mock
  private GridRepository gridRepository;

  private GridSaver gridSaver;

  @BeforeEach
  void setUp() {
    gridSaver = new GridSaver(gridRepository);
  }

  private Grid newGrid() {
    return Grid.builder().x(60).y(127).build();
  }

  @Test
  @DisplayName("정상 저장이면 saveAndFlush로 즉시 반영한다")
  void savesAndFlushesGrid() {
    // given
    Grid grid = newGrid();

    // when
    gridSaver.saveInNewTransaction(grid);

    // then
    verify(gridRepository).saveAndFlush(grid);
  }

  @Test
  @DisplayName("동시 저장으로 유니크 제약 위반이 나면 예외를 그대로 던진다 (호출부에서 잡아야 하므로 여기선 삼키지 않는다)")
  void propagatesDataIntegrityViolationOnConcurrentSave() {
    // given
    Grid grid = newGrid();
    given(gridRepository.saveAndFlush(any(Grid.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key"));

    // when & then
    assertThatThrownBy(() -> gridSaver.saveInNewTransaction(grid))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}