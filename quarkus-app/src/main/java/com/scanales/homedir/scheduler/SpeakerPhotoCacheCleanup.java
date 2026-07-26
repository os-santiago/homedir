package com.scanales.homedir.scheduler;

import com.scanales.homedir.service.SpeakerPhotoProxyService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SpeakerPhotoCacheCleanup {

  @Inject SpeakerPhotoProxyService photoProxyService;

  @Scheduled(cron = "0 0 3 * * ?") // 3 AM daily
  void cleanExpiredCache() {
    Log.info("Starting speaker photo cache cleanup");
    photoProxyService.cleanExpiredCache();
  }
}
