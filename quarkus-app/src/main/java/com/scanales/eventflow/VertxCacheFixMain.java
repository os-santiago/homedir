package com.scanales.eventflow;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Custom entry point that tunes Vert.x before Quarkus starts.
 *
 * <p>Some deployment environments mount the filesystem as read only. Vert.x normally extracts
 * classpath resources to a temporary directory, creating a {@code vertx-cache} folder under {@code
 * java.io.tmpdir}. When the filesystem is not writable, this fails and the application cannot
 * start.
 *
 * <p>This main class disables Vert.x file caching <em>and</em> classpath resolving so Vert.x no
 * longer attempts to create the cache directory.
 */
@QuarkusMain
public class VertxCacheFixMain {

  public static void main(String... args) {
    // Disable caching of extracted resources
    System.setProperty("vertx.disableFileCaching", "true");
    // Avoid extracting classpath resources entirely so no cache directory is needed
    System.setProperty("vertx.disableFileCPResolving", "true");
    Quarkus.run(args);
  }
}
