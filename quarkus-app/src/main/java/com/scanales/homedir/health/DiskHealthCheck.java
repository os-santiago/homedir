package com.scanales.homedir.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

@Readiness
@ApplicationScoped
public class DiskHealthCheck implements HealthCheck {

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @ConfigProperty(name = "homedir.health.disk.threshold", defaultValue = "52428800")
  long minFreeBytes;

  @Override
  public HealthCheckResponse call() {
    try {
      Path dataDir = Path.of(dataDirPath);
      if (!Files.exists(dataDir)) {
        return HealthCheckResponse.named("disk-space").down().build();
      }
      FileStore store = Files.getFileStore(dataDir);
      long usable = store.getUsableSpace();
      boolean healthy = usable >= minFreeBytes;
      return HealthCheckResponse.named("disk-space")
          .status(healthy)
          .withData("usableBytes", usable)
          .withData("minFreeBytes", minFreeBytes)
          .build();
    } catch (Exception e) {
      return HealthCheckResponse.named("disk-space").down().build();
    }
  }
}
