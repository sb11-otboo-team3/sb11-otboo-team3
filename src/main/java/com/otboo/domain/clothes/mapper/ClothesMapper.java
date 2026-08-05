package com.otboo.domain.clothes.mapper;

import com.otboo.domain.clothes.dto.response.ClothesAttributeResponse;
import com.otboo.domain.clothes.dto.response.ClothesResponse;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ClothesMapper {

    public ClothesResponse toResponse(
            Clothes clothes,
            List<ClothesAttribute> attributes,
            Map<UUID, List<String>> selectableValuesByDefinitionId
    ) {
        List<ClothesAttributeResponse> attributeResponses = attributes.stream()
                .map(attribute -> new ClothesAttributeResponse(
                        attribute.getDefinition().getId(),
                        attribute.getDefinition().getName(),

                        selectableValuesByDefinitionId.getOrDefault(attribute.getDefinition().getId(), List.of()),
                        attribute.getValue()
                ))
                .toList();

        return new ClothesResponse(
                clothes.getId(),
                clothes.getOwner().getId(),
                clothes.getName(),
                clothes.getImageUrl(),
                clothes.getType(),
                attributeResponses
        );
    }
}
