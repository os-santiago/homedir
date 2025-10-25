package com.scanales.eventflow.security;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

/** Adds default identity attributes for the embedded dev users. */
@ApplicationScoped
@IfBuildProfile("dev")
public class LocalDevIdentityAugmentor implements SecurityIdentityAugmentor {

  @Override
  public Uni<SecurityIdentity> augment(
      SecurityIdentity identity, AuthenticationRequestContext context) {
    boolean localAuthEnabled =
        ConfigProvider.getConfig()
            .getOptionalValue("app.auth.local-enabled", Boolean.class)
            .orElse(false);
    if (!localAuthEnabled || identity == null || identity.isAnonymous()) {
      return Uni.createFrom().item(identity);
    }
    boolean hasEmail = identity.getAttribute("email") != null;
    boolean hasName = identity.getAttribute("name") != null;
    if (hasEmail && hasName) {
      return Uni.createFrom().item(identity);
    }
    QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
    if (!hasEmail) {
      builder.addAttribute("email", identity.getPrincipal().getName());
    }
    if (!hasName) {
      builder.addAttribute("name", identity.getPrincipal().getName());
    }
    return Uni.createFrom().item(builder.build());
  }

  @Override
  public int priority() {
    return 0;
  }
}
