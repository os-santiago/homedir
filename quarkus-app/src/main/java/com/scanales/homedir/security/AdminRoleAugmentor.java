package com.scanales.homedir.security;

import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Adds the {@code admin} role to authenticated identities whose email matches the {@code
 * ADMIN_LIST} configuration property.
 */
@ApplicationScoped
public class AdminRoleAugmentor implements SecurityIdentityAugmentor {

  @Override
  public Uni<SecurityIdentity> augment(
      SecurityIdentity identity, AuthenticationRequestContext context) {
    if (identity == null || identity.isAnonymous()) {
      return Uni.createFrom().item(identity);
    }
    boolean canView = AdminUtils.canViewAdminBackoffice(identity);
    boolean canManage = AdminUtils.canManageAdminBackoffice(identity);
    boolean missingViewRole = canView && !identity.getRoles().contains(AdminUtils.ADMIN_VIEW_ROLE);
    boolean missingAdminRole = canManage && !identity.getRoles().contains(AdminUtils.ADMIN_ROLE);
    if (!missingViewRole && !missingAdminRole) {
      return Uni.createFrom().item(identity);
    }
    QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
    if (missingViewRole) {
      builder.addRole(AdminUtils.ADMIN_VIEW_ROLE);
    }
    if (missingAdminRole) {
      builder.addRole(AdminUtils.ADMIN_ROLE);
    }
    return Uni.createFrom().item(builder.build());
  }

  @Override
  public int priority() {
    return 1;
  }
}
