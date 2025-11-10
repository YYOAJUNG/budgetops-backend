
package com.budgetops.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
  @GetMapping("/") 
  public String root() { 
    return "BudgetOps API Server is running"; 
  }
  
  @GetMapping("/health") 
  public String health() { 
    return "ok"; 
  }
  
  @GetMapping("/api/hello") 
  public String hello() { 
    return "hello from spring"; 
  }
}
