package com.otboo.global.infrastructure.storage.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.exception.StorageDeleteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = {
    FileDeletionRetryServiceTest.RetryTestConfig.class
})
class FileDeletionRetryServiceTest {

  @Autowired
  private FileDeletionRetryService fileDeletionRetryService;

  @MockitoBean
  private FileStorage fileStorage;

  @Test
  @DisplayName("삭제가 계속 실패하면 최대 3회까지 재시도한 후 예외 없이 종료된다 (Recover 처리)")
  void deleteWithRetryRetriesUpToMaxAttemptsThenRecovers() {
    // given
    willThrow(new StorageDeleteException(new RuntimeException("S3 장애")))
        .given(fileStorage)
        .delete("some-key.png");

    // when & then
    // @Recover가 예외를 삼키고 정상 처리하므로, 호출 자체는 예외 없이 끝나야 한다.
    assertThatCode(() -> fileDeletionRetryService.deleteWithRetry("some-key.png"))
        .doesNotThrowAnyException();

    // 최초 1회 + 재시도 2회 = 총 3회 호출
    verify(fileStorage, times(3)).delete("some-key.png");
  }

  @Test
  @DisplayName("삭제가 첫 시도에 성공하면 재시도 없이 1회만 호출된다")
  void deleteWithRetrySucceedsOnFirstAttempt() {
    // given
    // fileStorage.delete()는 void라 별도 given 없이 기본적으로 아무 것도 안 하고 성공 처리됨

    // when
    fileDeletionRetryService.deleteWithRetry("clean-key.png");

    // then
    verify(fileStorage, times(1)).delete("clean-key.png");
  }

  @EnableRetry
  @Import(FileDeletionRetryService.class)
  static class RetryTestConfig {
  }
}