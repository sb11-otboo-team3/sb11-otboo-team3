package com.otboo.domain.feed.comment.mapper;

import com.otboo.domain.feed.comment.dto.response.FeedCommentDto;
import com.otboo.domain.feed.comment.entity.Comment;
import com.otboo.domain.user.mapper.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedCommentMapper {

  private final UserSummaryMapper userSummaryMapper;

  public FeedCommentDto toDto(Comment comment) {
    return new FeedCommentDto(
        comment.getId(),
        comment.getCreatedAt(),
        comment.getFeed().getId(),
        userSummaryMapper.toUserSummary(comment.getAuthor()),
        comment.getContent()
    );
  }

}
