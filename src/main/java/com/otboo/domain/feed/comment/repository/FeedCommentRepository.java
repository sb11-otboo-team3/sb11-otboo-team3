package com.otboo.domain.feed.comment.repository;

import com.otboo.domain.feed.comment.entity.Comment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedCommentRepository extends JpaRepository<Comment, UUID>, FeedCommentRepositoryCustom {

}
