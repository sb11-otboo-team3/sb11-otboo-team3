package com.otboo.domain.clothes.repository;

import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttributeSelectableValueRepository extends JpaRepository<AttributeSelectableValue, UUID> {

    Optional<AttributeSelectableValue> findByDefinitionAndValue(ClothesAttributeDefinition definition, String value);

    List<AttributeSelectableValue> findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(
            Collection<ClothesAttributeDefinition> definitions);
}
