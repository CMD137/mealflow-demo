package com.mealflow.authuser.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface AuthUserMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM user_account")
  long maxUserId();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM user_address")
  long maxAddressId();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM merchant_employee")
  long maxEmployeeId();

  @Select("""
      SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE UPPER(TABLE_NAME) = UPPER('user_address') AND UPPER(COLUMN_NAME) = UPPER(#{columnName})
      """)
  int countAddressColumn(String columnName);

  @Update("ALTER TABLE user_address ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE")
  int addAddressDefaultColumn();

  @Select("SELECT id, phone, nickname, status FROM user_account WHERE id = #{id}")
  @Results(id = "userMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "phone", property = "phone"),
      @Result(column = "nickname", property = "nickname"),
      @Result(column = "status", property = "status")
  })
  UserAccountRow findUser(long id);

  @Select("SELECT id, phone, nickname, status FROM user_account WHERE phone = #{phone}")
  @ResultMap("userMap")
  UserAccountRow findUserByPhone(String phone);

  @Insert("""
      INSERT INTO user_account (id, phone, nickname, status, create_time, update_time)
      VALUES (#{id}, #{phone}, #{nickname}, #{status}, #{now}, #{now})
      """)
  int insertUser(@Param("id") long id, @Param("phone") String phone, @Param("nickname") String nickname,
      @Param("status") String status, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE user_account
      SET nickname = #{nickname}, status = #{status}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateUserProfile(@Param("id") long id, @Param("nickname") String nickname,
      @Param("status") String status, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, user_id, contact_name, contact_phone, detail, is_default
      FROM user_address
      WHERE user_id = #{userId}
      ORDER BY is_default DESC, id
      """)
  @Results(id = "addressMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "contact_name", property = "contactName"),
      @Result(column = "contact_phone", property = "contactPhone"),
      @Result(column = "detail", property = "detail"),
      @Result(column = "is_default", property = "defaultAddress")
  })
  List<UserAddressRow> findAddresses(long userId);

  @Select("""
      SELECT id, user_id, contact_name, contact_phone, detail, is_default
      FROM user_address
      WHERE id = #{id}
      """)
  @ResultMap("addressMap")
  UserAddressRow findAddress(long id);

  @Insert("""
      INSERT INTO user_address (id, user_id, contact_name, contact_phone, detail, is_default, create_time, update_time)
      VALUES (#{id}, #{userId}, #{contactName}, #{contactPhone}, #{detail}, FALSE, #{now}, #{now})
      """)
  int insertAddress(@Param("id") long id, @Param("userId") long userId,
      @Param("contactName") String contactName, @Param("contactPhone") String contactPhone,
      @Param("detail") String detail, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE user_address
      SET contact_name = #{contactName}, contact_phone = #{contactPhone}, detail = #{detail}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateAddress(@Param("id") long id, @Param("contactName") String contactName,
      @Param("contactPhone") String contactPhone, @Param("detail") String detail, @Param("now") LocalDateTime now);

  @Delete("DELETE FROM user_address WHERE id = #{id}")
  int deleteAddress(long id);

  @Update("""
      UPDATE user_address
      SET is_default = FALSE, update_time = #{now}
      WHERE user_id = #{userId}
      """)
  int clearDefaultAddress(@Param("userId") long userId, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE user_address
      SET is_default = TRUE, update_time = #{now}
      WHERE id = #{id} AND user_id = #{userId}
      """)
  int setDefaultAddress(@Param("id") long id, @Param("userId") long userId, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, merchant_id, user_id, role_code, status
      FROM merchant_employee
      WHERE user_id = #{userId} AND status = 'ACTIVE'
      ORDER BY id
      LIMIT 1
      """)
  @Results(id = "employeeMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "role_code", property = "roleCode"),
      @Result(column = "status", property = "status")
  })
  MerchantEmployeeRow findActiveEmployeeByUserId(long userId);

  @Select("""
      SELECT permission_code
      FROM role_permission
      WHERE role_code = #{roleCode}
      ORDER BY permission_code
      """)
  List<String> findPermissions(String roleCode);

  @Select("""
      SELECT r.role_code, r.role_name, r.description, r.builtin
      FROM merchant_role r
      ORDER BY r.builtin DESC, r.role_code
      """)
  @Results(id = "roleMap", value = {
      @Result(column = "role_code", property = "roleCode"),
      @Result(column = "role_name", property = "roleName"),
      @Result(column = "description", property = "description"),
      @Result(column = "builtin", property = "builtin")
  })
  List<MerchantRoleRow> findRoles();

  @Select("""
      SELECT role_code, role_name, description, builtin
      FROM merchant_role
      WHERE role_code = #{roleCode}
      """)
  @ResultMap("roleMap")
  MerchantRoleRow findRole(String roleCode);

  @Insert("""
      INSERT INTO merchant_role (role_code, role_name, description, builtin, create_time, update_time)
      VALUES (#{roleCode}, #{roleName}, #{description}, #{builtin}, #{now}, #{now})
      """)
  int insertRole(@Param("roleCode") String roleCode, @Param("roleName") String roleName,
      @Param("description") String description, @Param("builtin") boolean builtin, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE merchant_role
      SET role_name = #{roleName}, description = #{description}, update_time = #{now}
      WHERE role_code = #{roleCode}
      """)
  int updateRole(@Param("roleCode") String roleCode, @Param("roleName") String roleName,
      @Param("description") String description, @Param("now") LocalDateTime now);

  @Delete("DELETE FROM role_permission WHERE role_code = #{roleCode}")
  int deleteRolePermissions(String roleCode);

  @Insert("""
      INSERT INTO role_permission (role_code, permission_code, create_time)
      VALUES (#{roleCode}, #{permissionCode}, #{now})
      """)
  int insertRolePermission(@Param("roleCode") String roleCode, @Param("permissionCode") String permissionCode,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, parent_id, menu_code, menu_name, path, permission_code, sort_order, visible
      FROM menu_permission
      ORDER BY sort_order, id
      """)
  @Results(id = "menuMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "parent_id", property = "parentId"),
      @Result(column = "menu_code", property = "menuCode"),
      @Result(column = "menu_name", property = "menuName"),
      @Result(column = "path", property = "path"),
      @Result(column = "permission_code", property = "permissionCode"),
      @Result(column = "sort_order", property = "sortOrder"),
      @Result(column = "visible", property = "visible")
  })
  List<MenuPermissionRow> findMenus();

  @Select("""
      SELECT m.id, m.parent_id, m.menu_code, m.menu_name, m.path, m.permission_code, m.sort_order, m.visible
      FROM menu_permission m
      JOIN role_permission rp ON rp.permission_code = m.permission_code
      WHERE rp.role_code = #{roleCode} AND m.visible = TRUE
      ORDER BY m.sort_order, m.id
      """)
  @ResultMap("menuMap")
  List<MenuPermissionRow> findMenusByRole(String roleCode);

  @Select("""
      SELECT e.id AS employee_id, e.merchant_id, e.user_id, u.phone, u.nickname,
             e.role_code, COALESCE(r.role_name, e.role_code) AS role_name, e.status
      FROM merchant_employee e
      JOIN user_account u ON u.id = e.user_id
      LEFT JOIN merchant_role r ON r.role_code = e.role_code
      WHERE e.merchant_id = #{merchantId}
      ORDER BY e.id
      """)
  @Results(id = "employeeDetailMap", value = {
      @Result(column = "employee_id", property = "employeeId"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "phone", property = "phone"),
      @Result(column = "nickname", property = "nickname"),
      @Result(column = "role_code", property = "roleCode"),
      @Result(column = "role_name", property = "roleName"),
      @Result(column = "status", property = "status")
  })
  List<EmployeeDetailRow> findEmployees(long merchantId);

  @Select("""
      SELECT e.id AS employee_id, e.merchant_id, e.user_id, u.phone, u.nickname,
             e.role_code, COALESCE(r.role_name, e.role_code) AS role_name, e.status
      FROM merchant_employee e
      JOIN user_account u ON u.id = e.user_id
      LEFT JOIN merchant_role r ON r.role_code = e.role_code
      WHERE e.id = #{employeeId}
      """)
  @ResultMap("employeeDetailMap")
  EmployeeDetailRow findEmployee(long employeeId);

  @Select("""
      SELECT id, merchant_id, user_id, role_code, status
      FROM merchant_employee
      WHERE merchant_id = #{merchantId} AND user_id = #{userId}
      """)
  @ResultMap("employeeMap")
  MerchantEmployeeRow findEmployeeByMerchantAndUser(@Param("merchantId") long merchantId, @Param("userId") long userId);

  @Insert("""
      INSERT INTO merchant_employee (id, merchant_id, user_id, role_code, status, create_time, update_time)
      VALUES (#{id}, #{merchantId}, #{userId}, #{roleCode}, #{status}, #{now}, #{now})
      """)
  int insertEmployee(@Param("id") long id, @Param("merchantId") long merchantId, @Param("userId") long userId,
      @Param("roleCode") String roleCode, @Param("status") String status, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE merchant_employee
      SET role_code = #{roleCode}, status = #{status}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateEmployee(@Param("id") long id, @Param("roleCode") String roleCode, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Insert("""
      INSERT INTO auth_token
        (token, user_id, role_code, merchant_id, expire_time, revoked, create_time, update_time)
      VALUES
        (#{token}, #{userId}, #{roleCode}, #{merchantId}, #{expireTime}, FALSE, #{now}, #{now})
      """)
  int insertToken(@Param("token") String token, @Param("userId") long userId,
      @Param("roleCode") String roleCode, @Param("merchantId") Long merchantId,
      @Param("expireTime") LocalDateTime expireTime, @Param("now") LocalDateTime now);

  @Select("""
      SELECT t.token, t.user_id, t.role_code, t.merchant_id, t.expire_time, t.revoked,
             u.phone, u.nickname, u.status
      FROM auth_token t
      JOIN user_account u ON u.id = t.user_id
      WHERE t.token = #{token}
      """)
  @Results(id = "tokenMap", value = {
      @Result(column = "token", property = "token"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "role_code", property = "roleCode"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "expire_time", property = "expireTime"),
      @Result(column = "revoked", property = "revoked"),
      @Result(column = "phone", property = "phone"),
      @Result(column = "nickname", property = "nickname"),
      @Result(column = "status", property = "status")
  })
  AuthTokenRow findToken(String token);
}
