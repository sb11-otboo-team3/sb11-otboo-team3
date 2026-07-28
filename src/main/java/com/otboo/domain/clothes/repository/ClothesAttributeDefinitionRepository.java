package com.otboo.domain.clothes.repository;

import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClothesAttributeDefinitionRepository extends JpaRepository<ClothesAttributeDefinition, UUID> {

    Optional<ClothesAttributeDefinition> findByName(String name);
}
