package com.scanales.eventflow.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Repository persisting notifications per user using a single writer thread. */
@ApplicationScoped
public class NotificationRepository {

  @Inject ObjectMapper mapper;
  @Inject NotificationConfig config;

  private Path baseDir;
  private ThreadPoolExecutor executor;
  private BlockingQueue<Runnable> queue;

  @PostConstruct
  void init() {
    baseDir =
        Paths.get(System.getProperty("eventflow.data.dir", "data"), "notifications");
    try {
      Files.createDirectories(baseDir);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create notifications directory", e);
    }
    int size = config.maxQueueSize > 0 ? config.maxQueueSize : 10000;
    queue = new ArrayBlockingQueue<>(size);
    executor =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            r -> {
              Thread t = new Thread(r, "notification-writer");
              t.setDaemon(true);
              return t;
            });
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }

  Path fileForUser(String userId) {
    return baseDir.resolve(userId + "-v1.json");
  }

  /** Loads notifications for a user. */
  public List<Notification> loadForUser(String userId) {
    Path f = fileForUser(userId);
    if (!Files.exists(f)) {
      return new ArrayList<>();
    }
    try {
      var type = mapper.getTypeFactory().constructCollectionType(List.class, Notification.class);
      return mapper.readValue(f.toFile(), type);
    } catch (IOException e) {
      return new ArrayList<>();
    }
  }

  /** Schedules persistence of the full list for a user. */
  public void replace(String userId, List<Notification> list) {
    // Avoid expensive serialization when the writer queue is full.
    if (queue.remainingCapacity() == 0) {
      return;
    }

    byte[] data;
    try {
      data = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Path f = fileForUser(userId);
    Runnable task =
        () -> {
          try {
            FileIO.atomicWrite(f, data);
          } catch (IOException e) {
            // ignore
          }
        };
    if (!queue.offer(task)) {
      // drop silently; guard will have already checked depth
    } else {
      executor.execute(task);
    }
  }

  public int queueDepth() {
    return queue.size();
  }

  public Path baseDir() {
    return baseDir;
  }
}
