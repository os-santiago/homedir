package com.scanales.homedir.security;

import jakarta.enterprise.context.RequestScoped;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Holds a per-request CSP nonce. Lazily generated so the first consumer (template resolver or
 * response filter) wins and both share the same value.
 */
@RequestScoped
public class CspService {

  // ponytail: 16 bytes -> ~22 base64url chars; unique enough per request, no DB needed.
  private static final SecureRandom RNG = new SecureRandom();

  private volatile String nonce;

  public synchronized String getNonce() {
    if (nonce == null) {
      byte[] bytes = new byte[16];
      RNG.nextBytes(bytes);
      nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    return nonce;
  }
}
