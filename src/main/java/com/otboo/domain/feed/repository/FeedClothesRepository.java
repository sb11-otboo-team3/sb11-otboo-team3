package com.otboo.domain.feed.repository;

import com.otboo.domain.feed.entity.FeedClothes;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedClothesRepository extends JpaRepository<FeedClothes, UUID> {

}
