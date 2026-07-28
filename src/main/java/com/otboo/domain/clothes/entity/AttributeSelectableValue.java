package com.otboo.domain.clothes.entity;

import com.otboo.global.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;


@Entity
@Table(
        name = "attribute_selectable_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"definition_id", "`value`"})
)
@Check(constraints = "display_order >= 0")
public class AttributeSelectableValue extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClothesAttributeDefinition definition;

    @Column(name = "`value`", nullable = false, length = 100)
    private String value;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected AttributeSelectableValue() {
    }

    public AttributeSelectableValue(ClothesAttributeDefinition definition, String value, int displayOrder) {
        this.definition = definition;
        this.value = value;
        this.displayOrder = displayOrder;
    }

    public ClothesAttributeDefinition getDefinition() {
        return definition;
    }

    public String getValue() {
        return value;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
