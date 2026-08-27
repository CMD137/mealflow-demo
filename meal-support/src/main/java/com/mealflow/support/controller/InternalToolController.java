package com.mealflow.support.controller;

import com.mealflow.common.api.Result;
import com.mealflow.support.dto.ToolInvokeRequest;
import com.mealflow.support.dto.ToolInvokeResponse;
import com.mealflow.support.service.ToolInvokeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal tool execution entry used ONLY by the Python agent runtime. It is intentionally not
 * routed through the gateway; the shared internal token (A4) is validated here.
 */
@RestController
@RequestMapping("/internal/support")
public class InternalToolController {
  private final ToolInvokeService toolInvokeService;

  public InternalToolController(ToolInvokeService toolInvokeService) {
    this.toolInvokeService = toolInvokeService;
  }

  @PostMapping("/tools/invoke")
  public Result<ToolInvokeResponse> invoke(@RequestHeader(value = "X-Internal-Token", required = false) String token,
      @Valid @RequestBody ToolInvokeRequest request) {
    return Result.ok(toolInvokeService.invoke(token, request));
  }
}
