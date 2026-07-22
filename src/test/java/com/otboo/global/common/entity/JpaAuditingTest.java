package com.otboo.global.common.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.otboo.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.format.annotation.DateTimeFormat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
public class JpaAuditingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void 엔티티_저장시_UUID와_생성수정시각이_자동으로_등록된다() {
        // given
        TestAuditedEntity entity = new TestAuditedEntity("테스트 엔티티");

        // when
        entityManager.persist(entity);
        entityManager.flush();

        // then
        assertAll(
                () -> assertNotNull(entity.getId()),
                () -> assertNotNull(entity.getCreatedAt()),
                () -> assertNotNull(entity.getUpdatedAt())
        );
    }
}
