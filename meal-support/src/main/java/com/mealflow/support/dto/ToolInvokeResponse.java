package com.mealflow.support.dto;

public record ToolInvokeResponse(boolean success, String tool, Object data, String errorCode, String errorMessage) {

  public static ToolInvokeResponse ok(String tool, Object data) {
    return new ToolInvokeResponse(true, tool, data, null, null);
  }

  public static ToolInvokeResponse error(String tool, String errorCode, String errorMessage) {
    return new ToolInvokeResponse(false, tool, null, errorCode, errorMessage);
  }
}
