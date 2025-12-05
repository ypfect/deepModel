package com.deepmodel.relation.controller;

/**
 * description:
 * @author pengfyu
 * @date 2025/9/19 11:13
 */

import com.deepmodel.util.MappingTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UtilController {

  @Autowired
  private MappingTransformer transformer;

  @GetMapping("/test")
  public String reload() throws Exception {
    return transformer.run();
  }
}
