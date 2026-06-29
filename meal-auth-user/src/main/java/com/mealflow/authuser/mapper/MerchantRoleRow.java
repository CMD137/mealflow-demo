package com.mealflow.authuser.mapper;

public class MerchantRoleRow {
  private String roleCode;
  private String roleName;
  private String description;
  private boolean builtin;

  public String getRoleCode() {
    return roleCode;
  }

  public void setRoleCode(String roleCode) {
    this.roleCode = roleCode;
  }

  public String getRoleName() {
    return roleName;
  }

  public void setRoleName(String roleName) {
    this.roleName = roleName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isBuiltin() {
    return builtin;
  }

  public void setBuiltin(boolean builtin) {
    this.builtin = builtin;
  }
}
