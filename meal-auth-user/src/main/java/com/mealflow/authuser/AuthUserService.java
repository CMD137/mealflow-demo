package com.mealflow.authuser;

import com.mealflow.authuser.api.AddressView;
import com.mealflow.authuser.api.LoginRequest;
import com.mealflow.authuser.api.LoginResponse;
import com.mealflow.authuser.api.UserView;
import com.mealflow.authuser.mapper.AuthUserMapper;
import com.mealflow.authuser.mapper.UserAccountRow;
import com.mealflow.authuser.mapper.UserAddressRow;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.infra.id.IdGenerator;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthUserService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final AuthUserMapper authUserMapper;

  public AuthUserService(AuthUserMapper authUserMapper) {
    this.authUserMapper = authUserMapper;
  }

  @PostConstruct
  void initializeIdGenerator() {
    idGenerator.ensureAtLeast("userAccount", authUserMapper.maxUserId());
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    UserAccountRow user = authUserMapper.findUserByPhone(request.phone());
    if (user == null) {
      long id = idGenerator.next("userAccount");
      authUserMapper.insertUser(id, request.phone(), "New User " + id, "NORMAL", LocalDateTime.now());
      user = authUserMapper.findUser(id);
    }
    return new LoginResponse(user.getId(), "demo-token-" + user.getId(), user.getNickname());
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

  private AddressView addressView(UserAddressRow address) {
    return new AddressView(address.getId(), address.getUserId(), address.getContactName(),
        address.getContactPhone(), address.getDetail());
  }
}
