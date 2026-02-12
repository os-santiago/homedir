package com.scanales.eventflow.cfp;

import com.scanales.eventflow.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CfpConfigService {

  @Inject PersistenceService persistenceService;

  @ConfigProperty(name = "cfp.submissions.max-per-user-per-event", defaultValue = "2")
  int configuredMaxSubmissionsPerUserPerEvent;

  // Default to true: the module is new and should warn users until explicitly disabled by admins.
  @ConfigProperty(name = "cfp.testing.enabled", defaultValue = "true")
  boolean configuredTestingModeEnabled;

  private final Object lock = new Object();
  private volatile long lastKnownMtime = Long.MIN_VALUE;
  private volatile CfpConfig runtime;

  @PostConstruct
  void init() {
    synchronized (lock) {
      refreshFromDisk(true);
      if (runtime == null) {
        runtime =
            CfpConfig.defaults(
                normalizeMaxSubmissions(configuredMaxSubmissionsPerUserPerEvent),
                configuredTestingModeEnabled);
      }
    }
  }

  public CfpConfig current() {
    synchronized (lock) {
      refreshFromDisk(false);
      return runtime;
    }
  }

  public int currentMaxSubmissionsPerUserPerEvent() {
    return current().maxSubmissionsPerUserPerEvent();
  }

  public boolean isTestingModeEnabled() {
    return current().testingModeEnabled();
  }

  public int updateMaxSubmissionsPerUserPerEvent(int requestedLimit) {
    synchronized (lock) {
      refreshFromDisk(false);
      int normalized = normalizeMaxSubmissions(requestedLimit);
      runtime = runtime == null ? CfpConfig.defaults(normalized, configuredTestingModeEnabled) : runtime.withMaxSubmissionsPerUserPerEvent(normalized);
      persistenceService.saveCfpConfigSync(runtime);
      lastKnownMtime = persistenceService.cfpConfigLastModifiedMillis();
      return runtime.maxSubmissionsPerUserPerEvent();
    }
  }

  public boolean updateTestingModeEnabled(boolean enabled) {
    synchronized (lock) {
      refreshFromDisk(false);
      runtime = runtime == null ? CfpConfig.defaults(normalizeMaxSubmissions(configuredMaxSubmissionsPerUserPerEvent), enabled) : runtime.withTestingModeEnabled(enabled);
      persistenceService.saveCfpConfigSync(runtime);
      lastKnownMtime = persistenceService.cfpConfigLastModifiedMillis();
      return runtime.testingModeEnabled();
    }
  }

  public CfpConfig update(Integer requestedLimit, Boolean requestedTestingModeEnabled) {
    synchronized (lock) {
      refreshFromDisk(false);
      CfpConfig next = runtime;
      if (next == null) {
        next =
            CfpConfig.defaults(
                normalizeMaxSubmissions(configuredMaxSubmissionsPerUserPerEvent),
                configuredTestingModeEnabled);
      }
      if (requestedLimit != null) {
        next = next.withMaxSubmissionsPerUserPerEvent(normalizeMaxSubmissions(requestedLimit));
      }
      if (requestedTestingModeEnabled != null) {
        next = next.withTestingModeEnabled(requestedTestingModeEnabled);
      }
      runtime = next;
      persistenceService.saveCfpConfigSync(runtime);
      lastKnownMtime = persistenceService.cfpConfigLastModifiedMillis();
      return runtime;
    }
  }

  // Used by Quarkus tests to avoid cross-test leakage through the persisted config file.
  void resetForTests() {
    synchronized (lock) {
      runtime =
          CfpConfig.defaults(
              normalizeMaxSubmissions(configuredMaxSubmissionsPerUserPerEvent),
              configuredTestingModeEnabled);
      persistenceService.saveCfpConfigSync(runtime);
      lastKnownMtime = persistenceService.cfpConfigLastModifiedMillis();
    }
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.cfpConfigLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    Optional<CfpConfig> loaded = persistenceService.loadCfpConfig();
    if (loaded.isPresent()) {
      runtime = loaded.get();
    } else if (runtime == null) {
      runtime =
          CfpConfig.defaults(
              normalizeMaxSubmissions(configuredMaxSubmissionsPerUserPerEvent),
              configuredTestingModeEnabled);
    }
    lastKnownMtime = mtime;
  }

  private static int normalizeMaxSubmissions(int rawValue) {
    if (rawValue <= 0) {
      return 2;
    }
    if (rawValue < CfpSubmissionService.MIN_SUBMISSIONS_PER_USER_PER_EVENT) {
      return CfpSubmissionService.MIN_SUBMISSIONS_PER_USER_PER_EVENT;
    }
    return Math.min(rawValue, CfpSubmissionService.MAX_SUBMISSIONS_PER_USER_PER_EVENT);
  }
}

