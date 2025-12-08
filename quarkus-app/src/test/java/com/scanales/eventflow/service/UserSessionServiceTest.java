package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.service.UserSessionService;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;

@QuarkusTest
public class UserSessionServiceTest {

    @Inject
    UserSessionService sessionService;

    @InjectMock
    SecurityIdentity identity;

    @InjectMock
    UserProfileService userProfileService;

    @Test
    public void testAnonymousSession() {
        when(identity.isAnonymous()).thenReturn(true);

        var session = sessionService.getCurrentSession();
        assertFalse(session.loggedIn());
        assertNull(session.displayName());
    }

    @Test
    public void testAuthenticatedSession_NoProfile() {
        // Mock Identity
        when(identity.isAnonymous()).thenReturn(false);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test@example.com");
        when(identity.getPrincipal()).thenReturn(principal);

        // Mock Attributes (simulating OIDC claims via identity attributes if
        // OidcJwtCallerPrincipal casting fails or fallback)
        // For this test we assume the service falls back to principal name for
        // email/name

        // Mock Profile Service (No profile yet)
        when(userProfileService.find("test@example.com")).thenReturn(Optional.empty());

        var session = sessionService.getCurrentSession();
        assertTrue(session.loggedIn());
        assertEquals("test@example.com", session.email());
        assertFalse(session.githubLinked());
    }

    @Test
    public void testAuthenticatedSession_WithGithub() {
        // Mock Identity
        when(identity.isAnonymous()).thenReturn(false);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("user@example.com");
        when(identity.getPrincipal()).thenReturn(principal);

        // Mock Profile with GitHub
        UserProfile profile = new UserProfile("user@example.com", "User Name", "user@example.com", null);
        UserProfile.GithubAccount gh = new UserProfile.GithubAccount("octocat", "url", "avatar", "123",
                java.time.Instant.now());
        profile.setGithub(gh);

        when(userProfileService.find("user@example.com")).thenReturn(Optional.of(profile));

        var session = sessionService.getCurrentSession();
        assertTrue(session.loggedIn());
        assertTrue(session.githubLinked());
        assertEquals("octocat", session.githubLogin());
    }
}
