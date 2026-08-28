package com.otboo.domain.feed.core.controller;

import com.otboo.domain.feed.core.search.FeedSearchReindexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feeds/search")
@RequiredArgsConstructor
public class FeedSearchAdminController {

  private final FeedSearchReindexService feedSearchReindexService;

  @PostMapping("/reindex")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Long> reindex() {
    long indexedCount = feedSearchReindexService.reindexAll();

    return ResponseEntity.ok(indexedCount);
  }
}