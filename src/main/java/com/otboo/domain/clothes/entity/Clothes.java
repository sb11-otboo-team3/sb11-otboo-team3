package com.otboo.domain.clothes.entity;

import com.otboo.domain.user.entity.User;
import com.otboo.global.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;


@Entity
@Table(
        name = "clothes",
        indexes = @Index(name = "idx_clothes_owner_id", columnList = "owner_id")
)
public class Clothes extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "image_key", length = 500)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClothesType type;

    protected Clothes() {
    }

    public Clothes(User owner, String name, String imageKey, ClothesType type) {
        this.owner = owner;
        this.name = name;
        this.imageKey = imageKey;
        this.type = type;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getImageKey() {
        return imageKey;
    }

    public ClothesType getType() {
        return type;
    }

    public void update(String name, ClothesType type) {
        this.name = name;
        this.type = type;
    }

    public void updateImageKey(String imageKey) {
        this.imageKey = imageKey;
    }
}
