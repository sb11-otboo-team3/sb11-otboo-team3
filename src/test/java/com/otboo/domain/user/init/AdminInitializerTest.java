package com.otboo.domain.user.init;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

  @Mock
  private AdminInitializationService adminInitializationService;

  @Test
  @DisplayName("초기화가 정상적으로 위임된다")
  void delegatesToInitializationService() throws Exception {
    // given
    AdminInitializer initializer = new AdminInitializer(adminInitializationService);

    // when
    initializer.run();

    // then
    verify(adminInitializationService).initializeAdmin();
  }

  @Test
  @DisplayName("초기화 중 예외가 발생해도 run()은 예외를 전파하지 않는다")
  void runDoesNotPropagateExceptionOnFailure() throws Exception {
    // given
    willThrow(new RuntimeException("DB 순간 장애"))
        .given(adminInitializationService).initializeAdmin();

    AdminInitializer initializer = new AdminInitializer(adminInitializationService);

    // when & then
    assertThatCode(() -> initializer.run())
        .doesNotThrowAnyException();
  }
}