package com.scanales.homedir.health;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class PersistenceHealthCheck implements HealthCheck {

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @Override
  public HealthCheckResponse call() {
    try {
      Path dataDir = Path.of(dataDirPath);
      boolean exists = Files.exists(dataDir);
      boolean writable = exists && Files.isWritable(dataDir);
      return HealthCheckResponse.named("persistence")
          .status(writable)
          .withData("exists", exists)
          .withData("writable", writable)
          .build();
    } catch (Exception e) {
      return HealthCheckResponse.named("persistence").down().build();
    }
  }
}
