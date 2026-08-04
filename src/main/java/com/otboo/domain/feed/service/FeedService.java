package com.otboo.domain.feed.service;

import com.otboo.domain.feed.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.dto.response.FeedDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

  @Transactional
  public FeedDto createFeed(FeedCreateRequest request, UUID currentUserId){
    return null;
  }

  @Transactional
  public FeedDto updateFeed(UUID feedId, FeedUpdateRequest request, UUID currentUserId){
    return null;
  }

  @Transactional
  public void deleteFeed(UUID feedId, UUID currentUserId){
  }

}
