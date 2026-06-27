package com.mealflow.authuser;

import com.mealflow.authuser.api.AddressView;
import com.mealflow.authuser.api.LoginRequest;
import com.mealflow.authuser.api.LoginResponse;
import com.mealflow.authuser.api.UserView;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AuthUserService {
  private final Map<Long, UserView> users = new ConcurrentHashMap<>();
  private final Map<Long, List<AddressView>> addresses = new ConcurrentHashMap<>();

  @PostConstruct
  void seed() {
    users.put(100L, new UserView(100L, "13800000000", "演示用户", "NORMAL"));
    users.put(101L, new UserView(101L, "13800000001", "排队用户A", "VIP"));
    users.put(102L, new UserView(102L, "13800000002", "排队用户B", "NORMAL"));
    addresses.put(100L, List.of(new AddressView(20L, 100L, "张三", "13800000000", "科技园 1 号楼")));
    addresses.put(101L, List.of(new AddressView(21L, 101L, "李四", "13800000001", "创业路 2 号")));
  }

  public LoginResponse login(LoginRequest request) {
    UserView user = users.values().stream()
        .filter(candidate -> candidate.phone().equals(request.phone()))
        .findFirst()
        .orElseGet(() -> {
          long id = 1000L + users.size();
          UserView created = new UserView(id, request.phone(), "新用户" + id, "NORMAL");
          users.put(id, created);
          return created;
        });
    return new LoginResponse(user.userId(), "demo-token-" + user.userId(), user.nickname());
  }

  public UserView get(long userId) {
    UserView user = users.get(userId);
    if (user == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
    }
    return user;
  }

  public List<AddressView> addresses(long userId) {
    get(userId);
    return addresses.getOrDefault(userId, List.of());
  }
}
