package com.scanales.homedir.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Content-Security-Policy header (issue #1029): header presence on HTML, nonce
 * uniqueness per request, key directives (script-src, form-action, report-uri), and the report
 * endpoint returning 204.
 */
@QuarkusTest
public class CspHeaderTest {

  @Test
  public void htmlHasCspWithNonce() {
    given()
        .when()
        .accept("text/html")
        .get("/")
        .then()
        .statusCode(200)
        .header("Content-Security-Policy", containsString("script-src 'self' 'nonce-"))
        .header("Content-Security-Policy", containsString("form-action 'self'"))
        .header("Content-Security-Policy", containsString("frame-ancestors 'none'"))
        .header("Content-Security-Policy", containsString("report-uri /csp-report"))
        .header(
            "Content-Security-Policy",
            containsString("style-src 'self' https://fonts.googleapis.com 'unsafe-inline'"))
        .body(containsString("nonce="));
  }

  @Test
  public void nonceDiffersBetweenRequests() {
    String h1 =
        given()
            .when()
            .accept("text/html")
            .get("/")
            .then()
            .extract()
            .header("Content-Security-Policy");
    String h2 =
        given()
            .when()
            .accept("text/html")
            .get("/")
            .then()
            .extract()
            .header("Content-Security-Policy");
    // Different per request since nonce is per-request
    MatcherAssert.assertThat(h1, not(equalTo(h2)));
  }

  @Test
  public void reportEndpointReturns204() {
    given()
        .when()
        .contentType("application/json")
        .body("{\"csp-report\":{\"document-uri\":\"https://example.test/\"}}")
        .post("/csp-report")
        .then()
        .statusCode(204);
  }

  @Test
  public void reportSanitizesQueryStringsFromUrls() {
    String body =
        "{\"csp-report\":{\"document-uri\":\"https://host/path?session=secret123&token=xyz\"}}";
    String out = CspReportResource.sanitize(body);
    // Query-string PII (session/token) must never reach logs verbatim.
    org.hamcrest.MatcherAssert.assertThat(out, not(containsString("secret123")));
    org.hamcrest.MatcherAssert.assertThat(out, not(containsString("xyz")));
    org.hamcrest.MatcherAssert.assertThat(out, containsString("?[redacted]"));
  }
}
