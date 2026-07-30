package com.otboo.domain.weather.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.service.GridSaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class GridSaverIntegrationTest {

  @Autowired
  private GridSaver gridSaver;

  @Autowired
  private GridRepository gridRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @AfterEach
  void tearDown() {
    gridRepository.deleteAll();
  }

  @Test
  @DisplayName("바깥 트랜잭션 도중 REQUIRES_NEW 저장이 중복으로 실패해도 바깥 트랜잭션은 정상 커밋된다")
  void outerTransactionSurvivesConflictingNestedSave() {
    // given: 동시에 다른 요청이 먼저 저장해 이미 커밋된 것처럼 준비
    Grid alreadySaved = Grid.builder().x(60).y(127).build();
    gridRepository.saveAndFlush(alreadySaved);

    Grid duplicate = Grid.builder().x(60).y(127).build();

    // when: WeatherServiceImpl.getLocation과 동일하게, 바깥 트랜잭션 안에서 REQUIRES_NEW 저장을 시도하고
    // 실패하면 호출부(바깥 트랜잭션 경계 안)에서 직접 잡은 뒤에도 다른 작업(다른 격자 저장)을 이어간다.
    // 이후 그 저장이 실제로 살아남아야 바깥 트랜잭션이 진짜로 커밋됐다는 게 증명된다 (읽기만 하면
    // 바깥 트랜잭션이 몰래 롤백돼도 검증에 걸리지 않는다).
    Grid another = Grid.builder().x(61).y(128).build();
    TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
    assertThatCode(() -> outerTransaction.executeWithoutResult(status -> {
      try {
        gridSaver.saveInNewTransaction(duplicate);
      } catch (DataIntegrityViolationException e) {
        // 동시성 충돌 - 무시하고 진행
      }
      gridRepository.save(another);
    })).doesNotThrowAnyException();

    // then: 바깥 트랜잭션이 오염되지 않고 정상 커밋됐다면, 기존 격자 + 바깥 트랜잭션에서 저장한 격자
    // 총 2개가 있어야 한다.
    assertThat(gridRepository.findAll()).hasSize(2);
  }
}
