package com.scanales.eventflow.notifications;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

/** Basic resource guards for memory, disk and queue depth. */
@ApplicationScoped
public class ResourceGuards {

  /** Checks whether the queue depth is below the maximum. */
  public boolean checkQueueDepth(int depth, int max) {
    return depth < max;
  }

  /** Checks disk space under the given directory. Returns true if enough space. */
  public boolean checkDiskBudget(Path dir, long minFreeBytes) {
    try {
      FileStore store = Files.getFileStore(dir);
      return store.getUsableSpace() > minFreeBytes;
    } catch (IOException e) {
      return true; // be permissive if unknown
    }
  }
}
