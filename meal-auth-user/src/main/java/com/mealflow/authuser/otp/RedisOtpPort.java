package com.mealflow.authuser.otp;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mealflow.auth.otp.mode", havingValue = "redis", matchIfMissing = true)
public class RedisOtpPort implements OtpPort {
  private static final Logger log = LoggerFactory.getLogger(RedisOtpPort.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Duration CODE_TTL = Duration.ofMinutes(5);
  private static final Duration COOLDOWN = Duration.ofSeconds(60);
  private static final int MAX_ATTEMPTS = 5;
  private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
      local value = redis.call('GET', KEYS[1])
      if not value then return 0 end
      local separator = string.find(value, '|')
      local codeHash = string.sub(value, 1, separator - 1)
      local attempts = tonumber(string.sub(value, separator + 1))
      if codeHash == ARGV[1] then
        redis.call('DEL', KEYS[1])
        return 1
      end
      attempts = attempts + 1
      if attempts >= tonumber(ARGV[2]) then
        redis.call('DEL', KEYS[1])
        return -2
      end
      local ttl = redis.call('PTTL', KEYS[1])
      if ttl > 0 then redis.call('PSETEX', KEYS[1], ttl, codeHash .. '|' .. attempts) end
      return -1
      """, Long.class);

  private final StringRedisTemplate redisTemplate;
  private final String pepper;
  private final boolean logCode;
  private final String testCode;

  public RedisOtpPort(StringRedisTemplate redisTemplate,
      @Value("${mealflow.auth.otp.pepper:mealflow-dev-otp-pepper}") String pepper,
      @Value("${mealflow.auth.otp.log-code:true}") boolean logCode,
      @Value("${mealflow.auth.otp.test-code:}") String testCode) {
    this.redisTemplate = redisTemplate;
    this.pepper = pepper;
    this.logCode = logCode;
    this.testCode = testCode;
  }

  @Override
  public void issueLoginCode(String phone) {
    Boolean accepted = redisTemplate.opsForValue().setIfAbsent(cooldownKey(phone), "1", COOLDOWN);
    if (!Boolean.TRUE.equals(accepted)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "verification code requested too frequently");
    }
    String code = testCode.isBlank() ? "%06d".formatted(RANDOM.nextInt(1_000_000)) : testCode;
    redisTemplate.opsForValue().set(codeKey(phone), hash(phone, code) + "|0", CODE_TTL);
    if (logCode) {
      log.info("login verification code issued for {}: {}", maskPhone(phone), code);
    }
  }

  @Override
  public void verifyLoginCode(String phone, String code) {
    if (code == null || code.isBlank()) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "verification code missing");
    }
    Long result = redisTemplate.execute(VERIFY_SCRIPT, java.util.List.of(codeKey(phone)), hash(phone, code),
        Integer.toString(MAX_ATTEMPTS));
    if (result == null || result <= 0) {
      throw new BizException(ErrorCode.UNAUTHORIZED,
          result != null && result == -2 ? "verification code has too many failed attempts" : "verification code invalid or expired");
    }
  }

  private String hash(String phone, String code) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest((pepper + ':' + phone + ':' + code).getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private String codeKey(String phone) {
    return "auth:otp:login:" + phone;
  }

  private String cooldownKey(String phone) {
    return "auth:otp:cooldown:" + phone;
  }

  private String maskPhone(String phone) {
    return phone.length() < 7 ? "***" : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }
}
