package com.otboo.global.common.entity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private static class TestSoftDeletableEntity extends SoftDeletableEntity {
    }
}