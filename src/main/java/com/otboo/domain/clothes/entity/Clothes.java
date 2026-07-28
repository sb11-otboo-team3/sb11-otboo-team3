package com.otboo.domain.clothes.entity;

import com.otboo.global.common.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;


@Entity
@Table(
        name = "clothes", indexes = @Index(name = "idx_clothes_owner_id", columnList = "owner_id")
)
public class Clothes extends SoftDeletableEntity {

    //users.id를 참조하지만 User 엔티티가 아직 없어 FK 연관관계 없이 UUID로만 저장한다.
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 30)
    private String type;

    protected Clothes() {
    }

    public Clothes(UUID ownerId, String name, String imageUrl, String type) {
        this.ownerId = ownerId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.type = type;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getType() {
        return type;
    }

}
