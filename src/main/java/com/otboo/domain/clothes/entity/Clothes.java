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

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClothesType type;

    protected Clothes() {
    }

    public Clothes(User owner, String name, String imageUrl, ClothesType type) {
        this.owner = owner;
        this.name = name;
        this.imageUrl = imageUrl;
        this.type = type;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public ClothesType getType() {
        return type;
    }

}
