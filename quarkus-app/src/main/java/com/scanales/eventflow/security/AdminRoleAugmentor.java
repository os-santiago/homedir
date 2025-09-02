package com.scanales.eventflow.security;

import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.identity.request.AuthenticationRequestContext;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Adds the {@code admin} role to authenticated identities whose email matches the
 * {@code ADMIN_LIST} configuration property.
 */
@ApplicationScoped
public class AdminRoleAugmentor implements SecurityIdentityAugmentor {

  @Override
  public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
    if (identity != null && AdminUtils.isAdmin(identity) && !identity.getRoles().contains("admin")) {
      return Uni.createFrom().item(QuarkusSecurityIdentity.builder(identity).addRole("admin").build());
    }
    return Uni.createFrom().item(identity);
  }

  @Override
  public int priority() {
    return 1;
  }
}
