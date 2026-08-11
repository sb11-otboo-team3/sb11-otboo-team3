package com.otboo.domain.feed.repository;

import com.otboo.domain.feed.entity.Comment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedCommentRepository extends JpaRepository<Comment, UUID>, FeedCommentRepositoryCustom {

}
