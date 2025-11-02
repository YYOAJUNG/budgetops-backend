package com.budgetops.backend.oauth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
  @GetMapping("/health") public String health() { return "ok"; }
  @GetMapping("/api/hello") public String hello() { return "hello from spring"; }
}
