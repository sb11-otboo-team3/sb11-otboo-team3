package com.otboo.domain.clothes.entity;

import com.otboo.global.common.entity.UpdatableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "clothes_attributes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"clothes_id", "definition_id"})
)
public class ClothesAttribute extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clothes_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Clothes clothes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private ClothesAttributeDefinition definition;

    @Column(name = "`value`", nullable = false, length = 100)
    private String value;

    protected ClothesAttribute() {
    }

    public ClothesAttribute(Clothes clothes, ClothesAttributeDefinition definition, String value) {
        this.clothes = clothes;
        this.definition = definition;
        this.value = value;
    }

    public Clothes getClothes() {
        return clothes;
    }

    public ClothesAttributeDefinition getDefinition() {
        return definition;
    }

    public String getValue() {
        return value;
    }
}
