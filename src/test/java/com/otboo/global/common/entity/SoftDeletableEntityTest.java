package com.otboo.global.common.entity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SoftDeletableEntityTest {

    @Test
    void 생성_직후에는_삭제시각이_없다() {
        // given
        TestSoftDeletableEntity entity = new TestSoftDeletableEntity();

        // when & then
        assertNull(entity.getDeletedAt());
    }

    @Test
    void 논리삭제하면_삭제시각이_기록된다() {
        // given
        TestSoftDeletableEntity entity = new TestSoftDeletableEntity();

        // when
        entity.delete();

        // then
        assertNotNull(entity.getDeletedAt());
    }

    @Test
    void 논리삭제를_여러번_호출해도_최초_삭제시각을_유지한다() {
        // given
        TestSoftDeletableEntity entity = new TestSoftDeletableEntity();
        entity.delete();
        Instant firstDeletedAt = entity.getDeletedAt();

        // when
        entity.delete();

        // then
        assertSame(firstDeletedAt, entity.getDeletedAt());
    }

    private static class TestSoftDeletableEntity extends SoftDeletableEntity {
    }
}