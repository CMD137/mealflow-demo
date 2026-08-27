package com.mealflow.authuser.otp;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mealflow.auth.otp.mode", havingValue = "memory")
public class InMemoryOtpPort implements OtpPort {
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  private final String testCode;
  private final Clock clock = Clock.systemUTC();

  public InMemoryOtpPort(@Value("${mealflow.auth.otp.test-code:123456}") String testCode) {
    this.testCode = testCode;
  }

  @Override
  public void issueLoginCode(String phone) {
    entries.put(phone, new Entry(testCode, clock.instant().plusSeconds(300), 0));
  }

  @Override
  public void verifyLoginCode(String phone, String code) {
    Entry entry = entries.get(phone);
    if (entry == null || entry.expireAt().isBefore(clock.instant()) || !entry.code().equals(code)) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "verification code invalid or expired");
    }
    entries.remove(phone, entry);
  }

  private record Entry(String code, Instant expireAt, int attempts) {
  }
}
