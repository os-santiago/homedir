package com.scanales.eventflow.agenda;

import com.scanales.eventflow.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AgendaProposalConfigService {

  @Inject PersistenceService persistenceService;

  @ConfigProperty(name = "events.agenda.proposal-notice.enabled", defaultValue = "true")
  boolean configuredProposalNoticeEnabled;

  private final Object lock = new Object();
  private volatile long lastKnownMtime = Long.MIN_VALUE;
  private volatile AgendaProposalConfig runtime;

  @PostConstruct
  void init() {
    synchronized (lock) {
      refreshFromDisk(true);
      if (runtime == null) {
        runtime = AgendaProposalConfig.defaults(configuredProposalNoticeEnabled);
      }
    }
  }

  public AgendaProposalConfig current() {
    synchronized (lock) {
      refreshFromDisk(false);
      return runtime;
    }
  }

  public boolean isProposalNoticeEnabled() {
    return current().proposalNoticeEnabled();
  }

  public boolean updateProposalNoticeEnabled(boolean enabled) {
    synchronized (lock) {
      refreshFromDisk(false);
      runtime =
          runtime == null
              ? AgendaProposalConfig.defaults(enabled)
              : runtime.withProposalNoticeEnabled(enabled);
      persistenceService.saveAgendaProposalConfigSync(runtime);
      lastKnownMtime = persistenceService.agendaProposalConfigLastModifiedMillis();
      return runtime.proposalNoticeEnabled();
    }
  }

  // Used by tests to avoid persisted cross-test leakage.
  public void resetForTests() {
    synchronized (lock) {
      runtime = AgendaProposalConfig.defaults(configuredProposalNoticeEnabled);
      persistenceService.saveAgendaProposalConfigSync(runtime);
      lastKnownMtime = persistenceService.agendaProposalConfigLastModifiedMillis();
    }
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.agendaProposalConfigLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    Optional<AgendaProposalConfig> loaded = persistenceService.loadAgendaProposalConfig();
    if (loaded.isPresent()) {
      runtime = loaded.get();
    } else if (runtime == null) {
      runtime = AgendaProposalConfig.defaults(configuredProposalNoticeEnabled);
    }
    lastKnownMtime = mtime;
  }
}
