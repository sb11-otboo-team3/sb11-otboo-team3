package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.exception.GridRegistrationFailedException;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GridResolverTest {

  @Mock
  private GridRepository gridRepository;

  @Mock
  private GridSaver gridSaver;

  private GridResolver gridResolver;

  @BeforeEach
  void setUp() {
    gridResolver = new GridResolver(gridRepository, gridSaver);
  }

  @Test
  @DisplayName("이미 있는 격자면 그대로 반환한다")
  void returnsExistingGridWithoutRegistering() {
    // given
    Grid existing = Grid.builder().x(60).y(127).build();
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existing));

    // when
    Grid result = gridResolver.findOrRegister(new WeatherGrid(60, 127));

    // then
    assertThat(result).isEqualTo(existing);
    verify(gridSaver, Mockito.never()).saveInNewTransaction(any(Grid.class));
  }

  @Test
  @DisplayName("없는 격자면 새로 등록해서 반환한다")
  void registersAndReturnsNewGridWhenMissing() {
    // given
    Grid registered = Grid.builder().x(60).y(127).build();
    given(gridRepository.findByXAndY(60, 127))
        .willReturn(Optional.empty(), Optional.of(registered));

    // when
    Grid result = gridResolver.findOrRegister(new WeatherGrid(60, 127));

    // then
    assertThat(result).isEqualTo(registered);
    ArgumentCaptor<Grid> captor = ArgumentCaptor.forClass(Grid.class);
    verify(gridSaver).saveInNewTransaction(captor.capture());
    assertThat(captor.getValue().getX()).isEqualTo(60);
    assertThat(captor.getValue().getY()).isEqualTo(127);
  }

  @Test
  @DisplayName("동시에 같은 격자가 먼저 등록돼 유니크 제약 위반이 나도, 재조회해서 정상 반환한다")
  void returnsGridEvenWhenConcurrentSaveConflicts() {
    // given
    Grid registeredByOther = Grid.builder().x(60).y(127).build();
    given(gridRepository.findByXAndY(60, 127))
        .willReturn(Optional.empty(), Optional.of(registeredByOther));
    Mockito.doThrow(new DataIntegrityViolationException("duplicate key"))
        .when(gridSaver).saveInNewTransaction(any(Grid.class));

    // when
    Grid result = gridResolver.findOrRegister(new WeatherGrid(60, 127));

    // then
    assertThat(result).isEqualTo(registeredByOther);
  }

  @Test
  @DisplayName("등록을 시도했는데도 재조회에서 끝내 못 찾으면 GridRegistrationFailedException을 던진다")
  void throwsWhenGridStillMissingAfterRegistrationAttempt() {
    // given
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> gridResolver.findOrRegister(new WeatherGrid(60, 127)))
        .isInstanceOf(GridRegistrationFailedException.class);
  }
}
