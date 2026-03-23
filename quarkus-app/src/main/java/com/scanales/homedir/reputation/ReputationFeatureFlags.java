package com.scanales.homedir.reputation;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ReputationFeatureFlags {

  @ConfigProperty(name = "reputation.engine.enabled", defaultValue = "false")
  boolean engineEnabled;

  @ConfigProperty(name = "reputation.hub.ui.enabled", defaultValue = "false")
  boolean hubUiEnabled;

  @ConfigProperty(name = "reputation.recognition.enabled", defaultValue = "false")
  boolean recognitionEnabled;

  @ConfigProperty(name = "reputation.shadow.read.enabled", defaultValue = "false")
  boolean shadowReadEnabled;

  @ConfigProperty(name = "reputation.profile.summary.enabled", defaultValue = "false")
  boolean profileSummaryEnabled;

  public Flags snapshot() {
    return new Flags(
        engineEnabled, hubUiEnabled, recognitionEnabled, shadowReadEnabled, profileSummaryEnabled);
  }

  public record Flags(
      boolean engineEnabled,
      boolean hubUiEnabled,
      boolean recognitionEnabled,
      boolean shadowReadEnabled,
      boolean profileSummaryEnabled) {}
}
