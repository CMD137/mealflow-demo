package com.mealflow.authuser.api;

public record MenuView(long menuId, Long parentId, String menuCode, String menuName, String path,
    String permissionCode, int sortOrder, boolean visible) {
}
