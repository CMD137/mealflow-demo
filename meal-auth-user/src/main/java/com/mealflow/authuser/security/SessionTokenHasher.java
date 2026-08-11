package com.mealflow.authuser.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SessionTokenHasher {
  private final String pepper;

  public SessionTokenHasher(@Value("${mealflow.auth.session.pepper:mealflow-dev-session-pepper}") String pepper) {
    this.pepper = pepper;
  }

  public String hash(String token) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest((pepper + ':' + token).getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
