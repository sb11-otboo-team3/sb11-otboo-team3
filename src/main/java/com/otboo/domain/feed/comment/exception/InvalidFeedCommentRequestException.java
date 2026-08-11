package com.otboo.domain.feed.comment.exception;

import com.otboo.domain.feed.core.exception.FeedException;
import org.springframework.http.HttpStatus;

public class InvalidFeedCommentRequestException extends FeedException {

  public InvalidFeedCommentRequestException() {
    super(HttpStatus.BAD_REQUEST, "경로의 피드 ID와 요청 본문의 피드 ID가 일치하지 않습니다.");
  }
}