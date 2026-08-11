package com.otboo.domain.feed.repository;

import com.otboo.domain.feed.entity.Comment;
import java.util.List;
import java.util.UUID;

public interface FeedCommentRepositoryCustom {

  List<Comment> findComments(
      UUID feedId,
      String cursor,
      UUID idAfter,
      int limit
  );

  long countComments(UUID feedId);
}
