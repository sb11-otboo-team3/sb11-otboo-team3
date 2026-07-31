package com.otboo.domain.clothes.mapper;

import com.otboo.domain.clothes.dto.response.ClothesAttributeDefinitionResponse;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClothesAttributeDefinitionMapper {

    public ClothesAttributeDefinitionResponse toResponse(
            ClothesAttributeDefinition definition,
            List<AttributeSelectableValue> selectableValues
    ) {
        List<String> values = selectableValues.stream()
                .map(AttributeSelectableValue::getValue)
                .toList();

        return new ClothesAttributeDefinitionResponse(
                definition.getId(),
                definition.getName(),
                values,
                definition.getCreatedAt()
        );
    }
}
