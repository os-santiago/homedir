package io.eventflow.notifications.global;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/** Persists the ring buffer of global notifications to a JSON file. */
@ApplicationScoped
public class GlobalNotificationRepository {
  private final ObjectMapper mapper = new ObjectMapper();
  private Path file;

  @PostConstruct
  void init() {
    file = Path.of("data", "notifications-global-ws.json");
    try {
      Files.createDirectories(file.getParent());
    } catch (IOException e) {
      // ignore
    }
  }

  public void save(Deque<GlobalNotification> buffer) {
    List<GlobalNotification> list = new ArrayList<>(buffer);
    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      mapper.writeValue(tmp.toFile(), list);
      Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      // ignore persistence errors for now
    }
  }

  public Deque<GlobalNotification> load() {
    if (!Files.exists(file)) {
      return new LinkedList<>();
    }
    try {
      List<GlobalNotification> list =
          mapper.readValue(file.toFile(), new TypeReference<List<GlobalNotification>>() {});
      return new LinkedList<>(list);
    } catch (IOException e) {
      return new LinkedList<>();
    }
  }
}
