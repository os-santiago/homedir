package com.scanales.homedir.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class I18nIntegrityTest {

  @Test
  void everyMessageMethodHasKeyInEnAndEsBundles() throws Exception {
    Set<String> enKeys = loadKeys("/i18n.properties");
    Set<String> esKeys = loadKeys("/i18n_es.properties");

    for (Method method : AppMessages.class.getDeclaredMethods()) {
      String key = method.getName();
      assertTrue(enKeys.contains(key), "Missing key '" + key + "' in i18n.properties");
      assertTrue(esKeys.contains(key), "Missing key '" + key + "' in i18n_es.properties");
    }
  }

  @Test
  void enAndEsBundlesHaveIdenticalKeySets() throws Exception {
    Set<String> enKeys = loadKeys("/i18n.properties");
    Set<String> esKeys = loadKeys("/i18n_es.properties");
    assertEquals(enKeys, esKeys, "i18n.properties and i18n_es.properties key sets differ");
  }

  private static Set<String> loadKeys(String resource) throws Exception {
    Properties properties = new Properties();
    try (InputStream in = I18nIntegrityTest.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Resource not found: " + resource);
      }
      properties.load(in);
    }
    Set<String> keys = new TreeSet<>();
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      keys.add((String) entry.getKey());
    }
    return keys;
  }
}
