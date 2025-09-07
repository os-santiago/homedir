package io.eventflow.home.now;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NowBoxTemplateTest {

  @Inject
  @Location("_now-box.qute.html")
  Template tmpl;

  @Test
  public void rendersEmptyModel() {
    tmpl.data("nowBox", new NowBoxView()).render();
  }
}
