package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Year;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests loading and unloading historical user schedules. */
public class UserScheduleHistoricalServiceTest {

  @TempDir
  Path tempDir;

  static class TestCapacityService extends CapacityService {
    Mode mode = Mode.ADMITTING;

    @Override
    public synchronized Status evaluate() {
      return new Status(mode, 0, 0, 0, java.time.Instant.now(), Trend.STABLE);
    }

    @Override
    public Status getStatus() {
      return evaluate();
    }
  }

  private UserScheduleService newService(TestCapacityService cap) throws Exception {
    System.setProperty("homedir.data.dir", tempDir.toString());
    PersistenceService ps = new PersistenceService();
    ps.objectMapper = new ObjectMapper();
    ps.init();
    UserScheduleService svc = new UserScheduleService();
    Field f = UserScheduleService.class.getDeclaredField("persistence");
    f.setAccessible(true);
    f.set(svc, ps);
    Field fc = UserScheduleService.class.getDeclaredField("capacity");
    fc.setAccessible(true);
    fc.set(svc, cap);
    svc.init();
    return svc;
  }

  private PersistenceService getPersistence(UserScheduleService svc) throws Exception {
    Field f = UserScheduleService.class.getDeclaredField("persistence");
    f.setAccessible(true);
    return (PersistenceService) f.get(svc);
  }

  private void flush(UserScheduleService svc) throws Exception {
    getPersistence(svc).flush();
    svc.shutdown();
  }

  @Test
  public void loadAndUnloadHistorical() throws Exception {
    TestCapacityService cap = new TestCapacityService();
    int year = Year.now().getValue() - 2;
    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValue(
        tempDir.resolve("user-schedule-" + year + ".json").toFile(),
        Map.of("u", Map.of("t1", new UserScheduleService.TalkDetails())));
    UserScheduleService svc = newService(cap);
    try {
      assertEquals(UserScheduleService.LoadStatus.LOADED, svc.loadHistorical(year));
      assertTrue(svc.getHistoricalTalkDetailsForUser(year, "u").containsKey("t1"));
      svc.unloadHistorical(year);
      assertTrue(svc.getHistoricalTalkDetailsForUser(year, "u").isEmpty());
    } finally {
      flush(svc);
    }
  }

  @Test
  public void returnsNoDataWhenMissing() throws Exception {
    TestCapacityService cap = new TestCapacityService();
    int year = Year.now().getValue() - 2;
    UserScheduleService svc = newService(cap);
    try {
      assertEquals(UserScheduleService.LoadStatus.NO_DATA, svc.loadHistorical(year));
    } finally {
      flush(svc);
    }
  }

  @Test
  public void capacityBlocksLoad() throws Exception {
    TestCapacityService cap = new TestCapacityService();
    cap.mode = CapacityService.Mode.CONTAINING;
    int year = Year.now().getValue() - 2;
    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValue(
        tempDir.resolve("user-schedule-" + year + ".json").toFile(),
        Map.of("u", Map.of("t1", new UserScheduleService.TalkDetails())));
    UserScheduleService svc = newService(cap);
    try {
      assertEquals(UserScheduleService.LoadStatus.CAPACITY, svc.loadHistorical(year));
      assertTrue(svc.getHistoricalTalkDetailsForUser(year, "u").isEmpty());
    } finally {
      flush(svc);
    }
  }
}
