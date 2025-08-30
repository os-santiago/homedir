package io.eventflow.notifications.global;

import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import java.io.StringReader;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.restassured.http.ContentType;

@QuarkusTest
public class AdminNotificationResourceTest {

  @TestHTTPResource("ws/global-notifications") URI httpUri;
  @Inject GlobalNotificationService service;

  @BeforeEach
  public void clear() {
    for (GlobalNotification g : service.latest(1000)) {
      service.removeById(g.id);
    }
  }

  private WsClient connect() throws Exception {
    URI wsUri = URI.create("ws://" + httpUri.getAuthority() + "/ws/global-notifications");
    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    WsClient client = new WsClient();
    c.connectToServer(client, ClientEndpointConfig.Builder.create().build(), wsUri);
    return client;
  }

  private void drain(WsClient c) throws InterruptedException {
    while (c.messages.poll(200, TimeUnit.MILLISECONDS) != null) {
      // discard backlog
    }
  }

  static class WsClient extends Endpoint {
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    @Override
    public void onOpen(Session session, EndpointConfig config) {
      session.addMessageHandler(String.class, messages::add);
      session.getAsyncRemote().sendText("{\"t\":\"hello\",\"cursor\":0}");
    }
  }

  @Test
  @TestSecurity(user = "admin", roles = {"admin"})
  public void broadcastEndpointBroadcastsToWsClients() throws Exception {
    WsClient c1 = connect();
    WsClient c2 = connect();
    // drain hello-ack and backlog
    c1.messages.poll(5, TimeUnit.SECONDS);
    c2.messages.poll(5, TimeUnit.SECONDS);
    drain(c1);
    drain(c2);
    String uniqueType = "ANN" + java.util.UUID.randomUUID();
    Map<String, Object> body = Map.of(
        "type", uniqueType,
        "title", "t",
        "message", "m");
    String id = given().contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/admin/api/notifications/broadcast")
        .then()
        .statusCode(200)
        .extract().path("id");
    String m1 = c1.messages.poll(5, TimeUnit.SECONDS);
    String m2 = c2.messages.poll(5, TimeUnit.SECONDS);
    assertNotNull(m1);
    assertNotNull(m2);
    JsonObject j1 = Json.createReader(new StringReader(m1)).readObject();
    JsonObject j2 = Json.createReader(new StringReader(m2)).readObject();
    assertEquals(id, j1.getString("id"));
    assertEquals(id, j2.getString("id"));

    String latestJson = given()
        .when()
        .get("/admin/api/notifications/latest?limit=10")
        .then()
        .statusCode(200)
        .extract().asString();
    JsonArray arr = Json.createReader(new StringReader(latestJson)).readArray();
    boolean found = arr.stream()
        .map(v -> ((JsonObject) v).getString("id"))
        .anyMatch(id::equals);
    assertTrue(found);
    service.removeById(id);
  }

  @Test
  @TestSecurity(user = "admin", roles = {"admin"})
  public void deleteRemovesFromBacklog() throws Exception {
    String uniqueType = "ANN" + java.util.UUID.randomUUID();
    Map<String, Object> body = Map.of(
        "type", uniqueType,
        "title", "to-delete",
        "message", "x");
    String id = given().contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/admin/api/notifications/broadcast")
        .then()
        .statusCode(200)
        .extract().path("id");
    given().when().delete("/admin/api/notifications/" + id).then().statusCode(204);

    WsClient c = connect();
    c.messages.poll(5, TimeUnit.SECONDS); // hello-ack
    List<String> msgs = new ArrayList<>();
    String m;
    while ((m = c.messages.poll(1, TimeUnit.SECONDS)) != null) {
      msgs.add(m);
    }
    boolean exists = msgs.stream()
        .map(str -> Json.createReader(new StringReader(str)).readObject().getString("id"))
        .anyMatch(id::equals);
    assertFalse(exists);
  }

  @Test
  public void dedupePreventsDuplicates() {
    GlobalNotification n = new GlobalNotification();
    n.id = "dedupe1";
    n.type = "TEST";
    n.title = "t";
    n.message = "m";
    n.dedupeKey = "dk";
    assertTrue(service.enqueue(n));
    assertFalse(service.enqueue(n));
    service.removeById("dedupe1");
  }
}
