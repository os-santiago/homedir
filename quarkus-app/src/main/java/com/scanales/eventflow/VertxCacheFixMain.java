package com.scanales.eventflow;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/** Custom entry point that disables Vert.x file caching before the application starts. */
@QuarkusMain
public class VertxCacheFixMain {

  public static void main(String... args) {
    // Disable Vert.x file caching to avoid permission warnings on some filesystems
    System.setProperty("vertx.disableFileCaching", "true");
    Quarkus.run(args);
  }
}
