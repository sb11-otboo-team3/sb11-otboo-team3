package com.otboo.global.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestProtectedController {

  @GetMapping("/api/test/protected")
  public String protectedEndpoint() {
    return "ok";
  }
}