package com.otboo.domain.clothes.entity;

import com.otboo.global.common.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "clothes_attribute_definitions")
public class ClothesAttributeDefinition extends SoftDeletableEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected ClothesAttributeDefinition() {
    }

    public ClothesAttributeDefinition(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
