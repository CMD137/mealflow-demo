package com.mealflow.authuser;

import com.mealflow.authuser.api.AddressView;
import com.mealflow.authuser.api.AddressRequest;
import com.mealflow.authuser.api.EmployeeRequest;
import com.mealflow.authuser.api.EmployeeView;
import com.mealflow.authuser.api.LoginRequest;
import com.mealflow.authuser.api.LoginResponse;
import com.mealflow.authuser.api.MenuView;
import com.mealflow.authuser.api.RoleRequest;
import com.mealflow.authuser.api.RoleView;
import com.mealflow.authuser.api.SignInView;
import com.mealflow.authuser.api.TokenPrincipalView;
import com.mealflow.authuser.api.UserView;
import com.mealflow.authuser.mapper.AuthUserMapper;
import com.mealflow.authuser.mapper.AuthTokenRow;
import com.mealflow.authuser.mapper.EmployeeDetailRow;
import com.mealflow.authuser.mapper.MenuPermissionRow;
import com.mealflow.authuser.mapper.MerchantEmployeeRow;
import com.mealflow.authuser.mapper.MerchantRoleRow;
import com.mealflow.authuser.mapper.UserAccountRow;
import com.mealflow.authuser.mapper.UserAddressRow;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.infra.id.IdGenerator;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthUserService {
  private static final String CUSTOMER_ROLE = "CUSTOMER";
  private static final Duration TOKEN_TTL = Duration.ofDays(7);
  private static final String SIGN_KEY_PREFIX = "sign:user:";
  private static final String SIGN_POINTS_SUFFIX = ":points";
  private static final String SIGN_DAYS_SUFFIX = ":days";

  private final IdGenerator idGenerator = new IdGenerator();
  private final AuthUserMapper authUserMapper;
  private final StringRedisTemplate redisTemplate;

  public AuthUserService(AuthUserMapper authUserMapper, StringRedisTemplate redisTemplate) {
    this.authUserMapper = authUserMapper;
    this.redisTemplate = redisTemplate;
  }

  @PostConstruct
  void initializeIdGenerator() {
    ensureAddressDefaultColumn();
    idGenerator.ensureAtLeast("userAccount", authUserMapper.maxUserId());
    idGenerator.ensureAtLeast("userAddress", authUserMapper.maxAddressId());
    idGenerator.ensureAtLeast("merchantEmployee", authUserMapper.maxEmployeeId());
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    UserAccountRow user = authUserMapper.findUserByPhone(request.phone());
    if (user == null) {
      long id = idGenerator.next("userAccount");
      authUserMapper.insertUser(id, request.phone(), "New User " + id, "NORMAL", LocalDateTime.now());
      user = authUserMapper.findUser(id);
    }
    TokenPrincipalView principal = principalFor(user);
    String token = "mf-" + UUID.randomUUID();
    LocalDateTime now = LocalDateTime.now();
    authUserMapper.insertToken(token, user.getId(), principal.roleCode(), principal.merchantId(), now.plus(TOKEN_TTL), now);
    return new LoginResponse(user.getId(), token, user.getNickname(), principal.roleCode(), principal.merchantId(),
        principal.permissions(), principal.menus());
  }

  public TokenPrincipalView validateToken(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    AuthTokenRow row = authUserMapper.findToken(token);
    if (row == null || row.isRevoked() || row.getExpireTime().isBefore(LocalDateTime.now())
        || "DISABLED".equals(row.getStatus())) {
      return null;
    }
    String roleCode = row.getRoleCode();
    Long merchantId = row.getMerchantId();
    if (merchantId != null) {
      MerchantEmployeeRow employee = authUserMapper.findActiveEmployeeByUserId(row.getUserId());
      if (employee == null || employee.getMerchantId() != merchantId) {
        return null;
      }
      roleCode = employee.getRoleCode();
      merchantId = employee.getMerchantId();
    }
    return principalView(row.getUserId(), row.getPhone(), row.getNickname(), roleCode, merchantId);
  }

  public UserView get(long userId) {
    UserAccountRow user = authUserMapper.findUser(userId);
    if (user == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "user not found");
    }
    return new UserView(user.getId(), user.getPhone(), user.getNickname(), user.getStatus());
  }

  public List<AddressView> addresses(long userId) {
    get(userId);
    return authUserMapper.findAddresses(userId).stream().map(this::addressView).toList();
  }

  public SignInView signInfo(long userId) {
    get(userId);
    return signView(userId, LocalDate.now(), 0);
  }

  @Transactional
  public synchronized SignInView signIn(long userId) {
    get(userId);
    LocalDate today = LocalDate.now();
    int rewardPoints = rewardPoints(userId, today);
    Boolean alreadySigned = redisTemplate.opsForValue().setBit(signKey(userId, YearMonth.from(today)),
        today.getDayOfMonth() - 1L, true);
    if (!Boolean.TRUE.equals(alreadySigned)) {
      redisTemplate.opsForValue().increment(pointsKey(userId), rewardPoints);
      redisTemplate.opsForValue().increment(daysKey(userId));
      return signView(userId, today, rewardPoints);
    }
    return signView(userId, today, 0);
  }

  @Transactional
  public AddressView addAddress(long userId, AddressRequest request) {
    get(userId);
    long id = idGenerator.next("userAddress");
    authUserMapper.insertAddress(id, userId, request.contactName(), request.phone(), request.detail(),
        LocalDateTime.now());
    return addressView(authUserMapper.findAddress(id));
  }

  @Transactional
  public AddressView updateAddress(long userId, long addressId, AddressRequest request) {
    UserAddressRow address = requireAddress(userId, addressId);
    authUserMapper.updateAddress(address.getId(), request.contactName(), request.phone(), request.detail(),
        LocalDateTime.now());
    return addressView(authUserMapper.findAddress(address.getId()));
  }

  @Transactional
  public void deleteAddress(long userId, long addressId) {
    UserAddressRow address = requireAddress(userId, addressId);
    authUserMapper.deleteAddress(address.getId());
  }

  @Transactional
  public AddressView setDefaultAddress(long userId, long addressId) {
    UserAddressRow address = requireAddress(userId, addressId);
    LocalDateTime now = LocalDateTime.now();
    authUserMapper.clearDefaultAddress(userId, now);
    authUserMapper.setDefaultAddress(address.getId(), userId, now);
    return addressView(authUserMapper.findAddress(address.getId()));
  }

  public List<MenuView> menus() {
    return authUserMapper.findMenus().stream().map(this::menuView).toList();
  }

  public List<RoleView> roles() {
    return authUserMapper.findRoles().stream().map(this::roleView).toList();
  }

  @Transactional
  public RoleView saveRole(RoleRequest request) {
    LocalDateTime now = LocalDateTime.now();
    MerchantRoleRow role = authUserMapper.findRole(request.roleCode());
    String description = request.description() == null ? "" : request.description();
    if (role == null) {
      authUserMapper.insertRole(request.roleCode(), request.roleName(), description, false, now);
    } else {
      authUserMapper.updateRole(request.roleCode(), request.roleName(), description, now);
    }
    authUserMapper.deleteRolePermissions(request.roleCode());
    for (String permission : request.permissions()) {
      if (permission == null || permission.isBlank()) {
        throw new BizException(ErrorCode.BAD_REQUEST, "permission must not be blank");
      }
      authUserMapper.insertRolePermission(request.roleCode(), permission, now);
    }
    return roleView(authUserMapper.findRole(request.roleCode()));
  }

  public List<EmployeeView> employees(long merchantId) {
    return authUserMapper.findEmployees(merchantId).stream().map(this::employeeView).toList();
  }

  @Transactional
  public EmployeeView addEmployee(long merchantId, EmployeeRequest request) {
    requireRole(request.roleCode());
    LocalDateTime now = LocalDateTime.now();
    UserAccountRow user = authUserMapper.findUserByPhone(request.phone());
    if (user == null) {
      long userId = idGenerator.next("userAccount");
      authUserMapper.insertUser(userId, request.phone(), request.nickname(), "NORMAL", now);
      user = authUserMapper.findUser(userId);
    } else {
      authUserMapper.updateUserProfile(user.getId(), request.nickname(), user.getStatus(), now);
    }

    MerchantEmployeeRow existing = authUserMapper.findEmployeeByMerchantAndUser(merchantId, user.getId());
    if (existing == null) {
      long employeeId = idGenerator.next("merchantEmployee");
      authUserMapper.insertEmployee(employeeId, merchantId, user.getId(), request.roleCode(), "ACTIVE", now);
      return employeeView(authUserMapper.findEmployee(employeeId));
    }
    authUserMapper.updateEmployee(existing.getId(), request.roleCode(), "ACTIVE", now);
    return employeeView(authUserMapper.findEmployee(existing.getId()));
  }

  @Transactional
  public EmployeeView changeEmployeeRole(long merchantId, long employeeId, String roleCode) {
    requireRole(roleCode);
    EmployeeDetailRow employee = requireEmployee(merchantId, employeeId);
    authUserMapper.updateEmployee(employee.getEmployeeId(), roleCode, employee.getStatus(), LocalDateTime.now());
    return employeeView(authUserMapper.findEmployee(employee.getEmployeeId()));
  }

  @Transactional
  public EmployeeView changeEmployeeStatus(long merchantId, long employeeId, String status) {
    if (!List.of("ACTIVE", "DISABLED").contains(status)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "employee status must be ACTIVE or DISABLED");
    }
    EmployeeDetailRow employee = requireEmployee(merchantId, employeeId);
    authUserMapper.updateEmployee(employee.getEmployeeId(), employee.getRoleCode(), status, LocalDateTime.now());
    return employeeView(authUserMapper.findEmployee(employee.getEmployeeId()));
  }

  private UserAddressRow requireAddress(long userId, long addressId) {
    UserAddressRow address = authUserMapper.findAddress(addressId);
    if (address == null || address.getUserId() != userId) {
      throw new BizException(ErrorCode.NOT_FOUND, "address not found");
    }
    return address;
  }

  private AddressView addressView(UserAddressRow address) {
    return new AddressView(address.getId(), address.getUserId(), address.getContactName(),
        address.getContactPhone(), address.getDetail(), address.isDefaultAddress());
  }

  private SignInView signView(long userId, LocalDate today, int todayRewardPoints) {
    YearMonth month = YearMonth.from(today);
    List<String> monthSignDates = monthSignDates(userId, month);
    return new SignInView(
        signed(userId, today),
        continuousSignDays(userId, today),
        totalDays(userId, monthSignDates.size()),
        totalPoints(userId),
        todayRewardPoints,
        monthSignDates);
  }

  private int continuousSignDays(long userId, LocalDate today) {
    int days = 0;
    LocalDate cursor = today;
    while (signed(userId, cursor)) {
      days++;
      cursor = cursor.minusDays(1);
    }
    return days;
  }

  private int rewardPoints(long userId, LocalDate today) {
    int nextContinuousDays = continuousSignDays(userId, today.minusDays(1)) + 1;
    return 5 + Math.min(nextContinuousDays, 7);
  }

  private boolean signed(long userId, LocalDate date) {
    Boolean value = redisTemplate.opsForValue().getBit(signKey(userId, YearMonth.from(date)),
        date.getDayOfMonth() - 1L);
    return Boolean.TRUE.equals(value);
  }

  private List<String> monthSignDates(long userId, YearMonth month) {
    List<String> dates = new ArrayList<>();
    String key = signKey(userId, month);
    for (int day = 1; day <= month.lengthOfMonth(); day++) {
      Boolean signed = redisTemplate.opsForValue().getBit(key, day - 1L);
      if (Boolean.TRUE.equals(signed)) {
        dates.add(month.atDay(day).toString());
      }
    }
    return dates;
  }

  private int totalPoints(long userId) {
    String value = redisTemplate.opsForValue().get(pointsKey(userId));
    return parseRedisCounter(value, 0);
  }

  private int totalDays(long userId, int fallback) {
    String value = redisTemplate.opsForValue().get(daysKey(userId));
    return parseRedisCounter(value, fallback);
  }

  private int parseRedisCounter(String value, int fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private String signKey(long userId, YearMonth month) {
    return SIGN_KEY_PREFIX + userId + ":" + month;
  }

  private String pointsKey(long userId) {
    return SIGN_KEY_PREFIX + userId + SIGN_POINTS_SUFFIX;
  }

  private String daysKey(long userId) {
    return SIGN_KEY_PREFIX + userId + SIGN_DAYS_SUFFIX;
  }

  private TokenPrincipalView principalFor(UserAccountRow user) {
    MerchantEmployeeRow employee = authUserMapper.findActiveEmployeeByUserId(user.getId());
    String roleCode = employee == null ? CUSTOMER_ROLE : employee.getRoleCode();
    Long merchantId = employee == null ? null : employee.getMerchantId();
    return principalView(user.getId(), user.getPhone(), user.getNickname(), roleCode, merchantId);
  }

  private TokenPrincipalView principalView(long userId, String phone, String nickname, String roleCode,
      Long merchantId) {
    return new TokenPrincipalView(userId, phone, nickname, roleCode, merchantId,
        authUserMapper.findPermissions(roleCode), authUserMapper.findMenusByRole(roleCode).stream()
            .map(this::menuView)
            .toList());
  }

  private RoleView roleView(MerchantRoleRow role) {
    return new RoleView(role.getRoleCode(), role.getRoleName(), role.getDescription(), role.isBuiltin(),
        authUserMapper.findPermissions(role.getRoleCode()));
  }

  private MenuView menuView(MenuPermissionRow row) {
    return new MenuView(row.getId(), row.getParentId(), row.getMenuCode(), row.getMenuName(), row.getPath(),
        row.getPermissionCode(), row.getSortOrder(), row.isVisible());
  }

  private EmployeeView employeeView(EmployeeDetailRow row) {
    return new EmployeeView(row.getEmployeeId(), row.getMerchantId(), row.getUserId(), row.getPhone(),
        row.getNickname(), row.getRoleCode(), row.getRoleName(), row.getStatus());
  }

  private MerchantRoleRow requireRole(String roleCode) {
    MerchantRoleRow role = authUserMapper.findRole(roleCode);
    if (role == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "role not found");
    }
    return role;
  }

  private EmployeeDetailRow requireEmployee(long merchantId, long employeeId) {
    EmployeeDetailRow employee = authUserMapper.findEmployee(employeeId);
    if (employee == null || employee.getMerchantId() != merchantId) {
      throw new BizException(ErrorCode.NOT_FOUND, "employee not found");
    }
    return employee;
  }

  private void ensureAddressDefaultColumn() {
    if (authUserMapper.countAddressColumn("is_default") == 0) {
      authUserMapper.addAddressDefaultColumn();
    }
    authUserMapper.hydrateSeedDefaultAddresses();
  }
}
