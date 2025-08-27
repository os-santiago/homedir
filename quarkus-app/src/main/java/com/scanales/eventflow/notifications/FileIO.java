package com.scanales.eventflow.notifications;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Simple atomic file write utility. */
public final class FileIO {
  private FileIO() {}

  public static void atomicWrite(Path path, byte[] data) throws IOException {
    Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
    Files.write(tmp, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
      ch.force(true);
    }
    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }
}
