package com.otboo.domain.feed.repository;

import com.otboo.domain.feed.entity.Feed;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

}
