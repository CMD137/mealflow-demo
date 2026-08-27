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
import com.mealflow.authuser.otp.OtpPort;
import com.mealflow.authuser.security.SessionTokenHasher;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.api.PageResult;
import com.mealflow.common.exception.BizException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthUserService {
  private static final Logger log = LoggerFactory.getLogger(AuthUserService.class);
  private static final String CUSTOMER_ROLE = "CUSTOMER";
  private static final String SIGN_IN_BIZ_TYPE = "SIGN_IN";
  private static final Duration TOKEN_TTL = Duration.ofDays(7);
  private static final String SIGN_KEY_PREFIX = "sign:user:";
  private static final String SIGN_POINTS_SUFFIX = ":points";
  private static final String SIGN_DAYS_SUFFIX = ":days";
  private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

  private final AuthDatabaseIdGenerator idGenerator;
  private final AuthUserMapper authUserMapper;
  private final StringRedisTemplate redisTemplate;
  private final OtpPort otpPort;
  private final SessionTokenHasher sessionTokenHasher;

  public AuthUserService(AuthUserMapper authUserMapper, StringRedisTemplate redisTemplate, OtpPort otpPort,
      SessionTokenHasher sessionTokenHasher, AuthDatabaseIdGenerator idGenerator) {
    this.authUserMapper = authUserMapper;
    this.redisTemplate = redisTemplate;
    this.otpPort = otpPort;
    this.sessionTokenHasher = sessionTokenHasher;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    otpPort.verifyLoginCode(request.phone(), request.code());
    UserAccountRow user = authUserMapper.findUserByPhone(request.phone());
    if (user == null) {
      long id = idGenerator.next("userAccount");
      authUserMapper.insertUser(id, request.phone(), "New User " + id, "NORMAL", LocalDateTime.now());
      user = authUserMapper.findUser(id);
    }
    TokenPrincipalView principal = principalFor(user);
    String token = newSessionToken();
    LocalDateTime now = LocalDateTime.now();
    authUserMapper.insertToken(sessionTokenHasher.hash(token), user.getId(), principal.roleCode(), principal.merchantId(),
        now.plus(TOKEN_TTL), now);
    return new LoginResponse(user.getId(), token, user.getNickname(), principal.roleCode(), principal.merchantId(),
        principal.permissions(), principal.menus());
  }

  public void requestLoginCode(String phone) {
    otpPort.issueLoginCode(phone);
  }

  public TokenPrincipalView validateToken(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    AuthTokenRow row = authUserMapper.findToken(sessionTokenHasher.hash(token));
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

  private String newSessionToken() {
    byte[] bytes = new byte[32];
    TOKEN_RANDOM.nextBytes(bytes);
    return "mf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
    String bizKey = today.toString();
    if (authUserMapper.findPointsLedger(userId, SIGN_IN_BIZ_TYPE, bizKey) != null) {
      // Already signed today: read-only no-op, never a double reward.
      return signView(userId, today, 0);
    }
    int reward = rewardPoints(userId, today);
    LocalDateTime now = LocalDateTime.now();
    if (authUserMapper.addUserPoints(userId, reward, now) != 1) {
      throw new BizException(ErrorCode.NOT_FOUND, "user not found");
    }
    int newBalance = authUserMapper.findUserPoints(userId);
    long ledgerId = idGenerator.next("pointsLedger");
    try {
      authUserMapper.insertPointsLedger(ledgerId, userId, SIGN_IN_BIZ_TYPE, bizKey, reward, newBalance, now);
    } catch (DuplicateKeyException ex) {
      // Concurrent duplicate sign-in: uk_points_ledger_biz wins and the points add rolls back with this tx.
      throw new BizException(ErrorCode.DUPLICATE, "already signed in today");
    }
    // Redis bitmap/counters are DERIVED caches; refresh only after the ledger transaction commits.
    // A Redis failure here must never affect the persisted fact, so it is logged, not thrown.
    afterCommit(() -> {
      try {
        redisTemplate.opsForValue().setBit(signKey(userId, YearMonth.from(today)), today.getDayOfMonth() - 1L, true);
        redisTemplate.opsForValue().increment(pointsKey(userId), reward);
        redisTemplate.opsForValue().increment(daysKey(userId));
      } catch (RuntimeException ex) {
        log.warn("failed to refresh derived sign-in cache for user {}: {}", userId, ex.getMessage());
      }
    });
    return signView(userId, today, reward);
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
    // Roles are platform-defined shared data in this school-project model. Letting one merchant
    // administrator edit them would change permissions for every merchant, so the UI is read-only.
    throw new BizException(ErrorCode.FORBIDDEN, "built-in roles are read-only");
  }

  public PageResult<EmployeeView> employees(long merchantId, int page, int pageSize) {
    int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
    int normalizedPage = Math.max(page, 1);
    long total = authUserMapper.countEmployees(merchantId);
    List<EmployeeView> items = authUserMapper.findEmployeesPage(merchantId, normalizedPageSize,
        (normalizedPage - 1) * normalizedPageSize).stream().map(this::employeeView).toList();
    return PageResult.of(items, total, normalizedPage, normalizedPageSize);
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
    MerchantEmployeeRow employeeInAnotherMerchant = authUserMapper.findEmployeeByUserId(user.getId());
    if (existing == null && employeeInAnotherMerchant != null) {
      throw new BizException(ErrorCode.DUPLICATE, "an employee account can belong to only one merchant");
    }
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
        monthSignDates.contains(today.toString()),
        continuousSignDaysIncluding(userId, today),
        monthSignDates.size(),
        totalPoints(userId),
        todayRewardPoints,
        monthSignDates);
  }

  /**
   * Continuous streak ending at {@code today} (inclusive), computed from the MySQL ledger.
   * A single rolling query covers the previous ~31 days, so a month boundary does not break
   * the streak.
   */
  private int continuousSignDaysIncluding(long userId, LocalDate today) {
    Set<String> recent = signKeysSince(userId, today.minusDays(31));
    int days = 0;
    LocalDate day = today;
    while (recent.contains(day.toString())) {
      days++;
      day = day.minusDays(1);
    }
    return days;
  }

  private int rewardPoints(long userId, LocalDate today) {
    // Today is not signed yet when reward is computed: streak including today = current streak + 1.
    return 5 + Math.min(continuousSignDaysIncluding(userId, today) + 1, 7);
  }

  private List<String> monthSignDates(long userId, YearMonth month) {
    return signKeysSince(userId, month.atDay(1))
        .stream()
        .filter(key -> key.startsWith(month.toString()))
        .toList();
  }

  private Set<String> signKeysSince(long userId, LocalDate sinceDate) {
    return authUserMapper.findPointsLedgerKeysSince(userId, SIGN_IN_BIZ_TYPE, sinceDate.atStartOfDay())
        .stream()
        .collect(java.util.stream.Collectors.toSet());
  }

  private int totalPoints(long userId) {
    return authUserMapper.findUserPoints(userId);
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

  /** Runs {@code action} after the current transaction commits (immediately when no tx is active). */
  private void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          action.run();
        }
      });
    } else {
      action.run();
    }
  }

}
