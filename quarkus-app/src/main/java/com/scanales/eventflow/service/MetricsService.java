package com.scanales.eventflow.service;

import com.sun.management.OperatingSystemMXBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.lang.management.ManagementFactory;

/** Provides simple system metrics for the admin dashboard. */
@ApplicationScoped
public class MetricsService {

  public record Metrics(double cpu, double memory, double disk, boolean lowDisk) {
  }

  @Inject
  PersistenceService persistenceService;

  public Metrics getMetrics() {
    double cpu = 0d;
    var os = ManagementFactory.getOperatingSystemMXBean();
    if (os instanceof OperatingSystemMXBean bean) {
      cpu = bean.getCpuLoad();
      if (cpu < 0)
        cpu = 0d; // can be -1 when undefined
    }
    Runtime rt = Runtime.getRuntime();
    double mem = 1d - ((double) rt.freeMemory() / (double) rt.totalMemory());
    double disk = persistenceService.getDiskUsage();
    return new Metrics(cpu * 100d, mem * 100d, disk * 100d, persistenceService.isLowDiskSpace());
  }
}
