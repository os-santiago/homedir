package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.homedir.TestDataDir;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
class ReputationRecognitionApiDisabledTest {

  @Test
  @TestSecurity(user = "validator@example.com")
  void recognitionReturnsConflictWhenFeatureIsDisabled() {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "target.one@example.com",
                "source_object_type",
                "community_content",
                "source_object_id",
                "thread-off",
                "recognition_type",
                "recommended"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(409)
        .body(containsString("recognition_disabled"));
  }
}
