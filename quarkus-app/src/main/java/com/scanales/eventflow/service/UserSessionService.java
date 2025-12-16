package com.scanales.eventflow.service;

import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.model.UserSession;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.security.Principal;
import java.util.Optional;

@RequestScoped
public class UserSessionService {

    @Inject
    SecurityIdentity identity;
    @Inject
    UserProfileService userProfileService;

    @Inject
    CommunityService communityService;

    // Optional: Inject UserInfo if available (standard in OIDC)
    // @Inject UserInfo userInfo;

    public UserSession getCurrentSession() {
        if (identity.isAnonymous()) {
            return UserSession.anonymous();
        }

        String email = getClaim(identity, "email");
        String name = getClaim(identity, "name");
        String picture = getClaim(identity, "picture"); // Google often sends 'picture'

        if (name == null) {
            name = identity.getPrincipal().getName();
        }
        if (email == null) {
            // Fallback to principal name if it looks like an email, or just use name
            email = identity.getPrincipal().getName();
        }

        // Default avatar if none provided
        if (picture == null) {
            // We can generate a simple one or leave null for the UI to handle
            picture = null;
        }

        // Check GitHub status via UserProfile
        Optional<UserProfile> profile = userProfileService.find(email);
        boolean githubLinked = false;
        String githubLogin = null;

        if (profile.isPresent()) {
            var gh = profile.get().getGithub();
            if (gh != null) {
                githubLinked = true;
                githubLogin = gh.login();
                if (gh.avatarUrl() != null && !gh.avatarUrl().isBlank()) {
                    picture = gh.avatarUrl();
                }
            }
        }

        // Community Member check:
        boolean communityMember = false;
        if (email != null) {
            communityMember = communityService.findByUserId(email).isPresent();
        }
        if (!communityMember && githubLogin != null) {
            communityMember = communityService.findByGithub(githubLogin).isPresent();
        }

        boolean isAdmin = com.scanales.eventflow.util.AdminUtils.isAdmin(identity);

        return new UserSession(true, name, email, picture, githubLinked, githubLogin, communityMember, isAdmin);
    }

    private String getClaim(SecurityIdentity identity, String claim) {
        Principal principal = identity.getPrincipal();
        if (principal instanceof OidcJwtCallerPrincipal oidc) {
            Object val = oidc.getClaim(claim);
            return val != null ? val.toString() : null;
        }
        // Access token usage if needed, or identity attributes
        return identity.getAttribute(claim) != null ? identity.getAttribute(claim).toString() : null;
    }
}
