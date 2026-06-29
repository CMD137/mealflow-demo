package com.mealflow.authuser;

import com.mealflow.authuser.api.AddressView;
import com.mealflow.authuser.api.AddressRequest;
import com.mealflow.authuser.api.LoginRequest;
import com.mealflow.authuser.api.LoginResponse;
import com.mealflow.authuser.api.TokenPrincipalView;
import com.mealflow.authuser.api.UserView;
import com.mealflow.authuser.mapper.AuthUserMapper;
import com.mealflow.authuser.mapper.AuthTokenRow;
import com.mealflow.authuser.mapper.MerchantEmployeeRow;
import com.mealflow.authuser.mapper.UserAccountRow;
import com.mealflow.authuser.mapper.UserAddressRow;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.infra.id.IdGenerator;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthUserService {
  private static final String CUSTOMER_ROLE = "CUSTOMER";
  private static final Duration TOKEN_TTL = Duration.ofDays(7);

  private final IdGenerator idGenerator = new IdGenerator();
  private final AuthUserMapper authUserMapper;

  public AuthUserService(AuthUserMapper authUserMapper) {
    this.authUserMapper = authUserMapper;
  }

  @PostConstruct
  void initializeIdGenerator() {
    idGenerator.ensureAtLeast("userAccount", authUserMapper.maxUserId());
    idGenerator.ensureAtLeast("userAddress", authUserMapper.maxAddressId());
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
        principal.permissions());
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
    return new TokenPrincipalView(row.getUserId(), row.getPhone(), row.getNickname(), row.getRoleCode(),
        row.getMerchantId(), authUserMapper.findPermissions(row.getRoleCode()));
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

  private UserAddressRow requireAddress(long userId, long addressId) {
    UserAddressRow address = authUserMapper.findAddress(addressId);
    if (address == null || address.getUserId() != userId) {
      throw new BizException(ErrorCode.NOT_FOUND, "address not found");
    }
    return address;
  }

  private AddressView addressView(UserAddressRow address) {
    return new AddressView(address.getId(), address.getUserId(), address.getContactName(),
        address.getContactPhone(), address.getDetail());
  }

  private TokenPrincipalView principalFor(UserAccountRow user) {
    MerchantEmployeeRow employee = authUserMapper.findActiveEmployeeByUserId(user.getId());
    String roleCode = employee == null ? CUSTOMER_ROLE : employee.getRoleCode();
    Long merchantId = employee == null ? null : employee.getMerchantId();
    return new TokenPrincipalView(user.getId(), user.getPhone(), user.getNickname(), roleCode, merchantId,
        authUserMapper.findPermissions(roleCode));
  }
}
