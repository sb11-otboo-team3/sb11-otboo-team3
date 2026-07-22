package com.otboo.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_audited_entities")
class TestAuditedEntity extends UpdatableEntity {

    @Column(nullable = false)
    private String name;

    protected  TestAuditedEntity() {
    }

    TestAuditedEntity(String name) {
        this.name = name;
    }
}
