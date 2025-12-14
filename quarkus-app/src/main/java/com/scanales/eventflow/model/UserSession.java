package com.scanales.eventflow.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Unified representation of the user's session state.
 * Combines OIDC identity (Google) and application profile data (GitHub
 * linking).
 */
@RegisterForReflection
public record UserSession(
        boolean loggedIn,
        String displayName,
        String email,
        String avatarUrl,
        boolean githubLinked,
        String githubLogin,
        boolean communityMember,
        boolean isAdmin) {

    public static UserSession anonymous() {
        return new UserSession(false, null, null, null, false, null, false, false);
    }
}
