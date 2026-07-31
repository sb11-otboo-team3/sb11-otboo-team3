package com.otboo.domain.clothes.repository;

import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeDefinitionRepository extends JpaRepository<ClothesAttributeDefinition, UUID> {

    Optional<ClothesAttributeDefinition> findByName(String name);

    List<ClothesAttributeDefinition> findByDeletedAtIsNullAndNameContainingIgnoreCase(String keyword, Sort sort);


}
