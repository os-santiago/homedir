package com.scanales.homedir.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityUtilsTest {

  @Test
  void testRedactSensitiveData_BearerToken() {
    String input = "Authorization: Bearer abc123def456ghi789jkl012mno345";
    String result = SecurityUtils.redactSensitiveData(input);
    assertTrue(result.contains("[REDACTED]"));
    assertFalse(result.contains("abc123def456ghi789jkl012mno345"));
  }

  @Test
  void testRedactSensitiveData_AccessToken() {
    String input = "access_token=gho_1234567890abcdefghijklmnopqrstuvwxyz";
    String result = SecurityUtils.redactSensitiveData(input);
    assertTrue(result.contains("access_token=[REDACTED]"));
    assertFalse(result.contains("gho_1234567890abcdefghijklmnopqrstuvwxyz"));
  }

  @Test
  void testRedactSensitiveData_IdToken() {
    String input = "id_token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ";
    String result = SecurityUtils.redactSensitiveData(input);
    assertTrue(result.contains("id_token=[REDACTED]"));
    assertFalse(result.contains("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"));
  }

  @Test
  void testRedactSensitiveData_Code() {
    String input = "code=4/0AY0e-g7QWERTYUIOPASDFGHJKLZXCVBNM";
    String result = SecurityUtils.redactSensitiveData(input);
    assertTrue(result.contains("code=[REDACTED]"));
    assertFalse(result.contains("4/0AY0e-g7QWERTYUIOPASDFGHJKLZXCVBNM"));
  }

  @Test
  void testRedactSensitiveData_State() {
    String input = "state=randomstatevalue12345";
    String result = SecurityUtils.redactSensitiveData(input);
    assertTrue(result.contains("state=[REDACTED]"));
    assertFalse(result.contains("randomstatevalue12345"));
  }

  @Test
  void testRedactSensitiveData_MultipleTokens() {
    String input = "access_token=token1&refresh_token=token2&code=code123";
    String result = SecurityUtils.redactSensitiveData(input);
    assertTrue(result.contains("access_token=[REDACTED]"));
    assertTrue(result.contains("refresh_token=[REDACTED]"));
    assertTrue(result.contains("code=[REDACTED]"));
    assertFalse(result.contains("token1"));
    assertFalse(result.contains("token2"));
    assertFalse(result.contains("code123"));
  }

  @Test
  void testRedactSensitiveData_NoSensitiveData() {
    String input = "user=john&email=john@example.com";
    String result = SecurityUtils.redactSensitiveData(input);
    assertEquals(input, result);
  }

  @Test
  void testRedactSensitiveData_NullInput() {
    String result = SecurityUtils.redactSensitiveData(null);
    assertEquals(null, result);
  }

  @Test
  void testRedactSensitiveData_EmptyInput() {
    String result = SecurityUtils.redactSensitiveData("");
    assertEquals("", result);
  }

  @Test
  void testRedactTokenPreview_ValidToken() {
    String token = "gho_1234567890abcdefghijklmnopqrstuvwxyz";
    String result = SecurityUtils.redactTokenPreview(token);
    assertEquals("...wxyz", result);
  }

  @Test
  void testRedactTokenPreview_ShortToken() {
    String token = "abc";
    String result = SecurityUtils.redactTokenPreview(token);
    assertEquals("[REDACTED]", result);
  }

  @Test
  void testRedactTokenPreview_NullToken() {
    String result = SecurityUtils.redactTokenPreview(null);
    assertEquals("[EMPTY]", result);
  }

  @Test
  void testRedactTokenPreview_EmptyToken() {
    String result = SecurityUtils.redactTokenPreview("");
    assertEquals("[EMPTY]", result);
  }

  @Test
  void testIsSensitiveParameter_Sensitive() {
    assertTrue(SecurityUtils.isSensitiveParameter("access_token"));
    assertTrue(SecurityUtils.isSensitiveParameter("ACCESS_TOKEN"));
    assertTrue(SecurityUtils.isSensitiveParameter("id_token"));
    assertTrue(SecurityUtils.isSensitiveParameter("refresh_token"));
    assertTrue(SecurityUtils.isSensitiveParameter("code"));
    assertTrue(SecurityUtils.isSensitiveParameter("state"));
    assertTrue(SecurityUtils.isSensitiveParameter("client_secret"));
    assertTrue(SecurityUtils.isSensitiveParameter("authorization"));
    assertTrue(SecurityUtils.isSensitiveParameter("password"));
    assertTrue(SecurityUtils.isSensitiveParameter("secret"));
    assertTrue(SecurityUtils.isSensitiveParameter("token"));
    assertTrue(SecurityUtils.isSensitiveParameter("key"));
    assertTrue(SecurityUtils.isSensitiveParameter("credentials"));
  }

  @Test
  void testIsSensitiveParameter_NotSensitive() {
    assertFalse(SecurityUtils.isSensitiveParameter("user"));
    assertFalse(SecurityUtils.isSensitiveParameter("email"));
    assertFalse(SecurityUtils.isSensitiveParameter("name"));
    assertFalse(SecurityUtils.isSensitiveParameter("redirect_uri"));
  }

  @Test
  void testIsSensitiveParameter_Null() {
    assertFalse(SecurityUtils.isSensitiveParameter(null));
  }

  @Test
  void testRedactIfSensitive_Sensitive() {
    String result = SecurityUtils.redactIfSensitive("access_token", "secret_value");
    assertEquals("[REDACTED]", result);
  }

  @Test
  void testRedactIfSensitive_NotSensitive() {
    String result = SecurityUtils.redactIfSensitive("user", "john");
    assertEquals("john", result);
  }

  @Test
  void testRedactIfSensitive_CaseInsensitive() {
    String result = SecurityUtils.redactIfSensitive("ACCESS_TOKEN", "secret_value");
    assertEquals("[REDACTED]", result);
  }
}
