package com.otboo.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class SoftDeletableEntity extends UpdatableEntity {

    @Column(nullable = true)
    private Instant deletedAt;

    public void delete() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }
}