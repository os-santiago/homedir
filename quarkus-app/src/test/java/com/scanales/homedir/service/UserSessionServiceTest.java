package com.scanales.homedir.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.scanales.homedir.model.UserProfile;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class UserSessionServiceTest {

  @Inject UserSessionService sessionService;

  @InjectMock SecurityIdentity identity;

  @InjectMock UserProfileService userProfileService;

  @Test
  public void testAnonymousSession() {
    when(identity.isAnonymous()).thenReturn(true);

    var session = sessionService.getCurrentSession();
    assertFalse(session.loggedIn());
    assertNull(session.displayName());
  }

  @Test
  public void testAuthenticatedSession_NoProfile() {
    when(identity.isAnonymous()).thenReturn(false);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test@example.com");
    when(identity.getPrincipal()).thenReturn(principal);

    when(userProfileService.find("test@example.com")).thenReturn(Optional.empty());

    var session = sessionService.getCurrentSession();
    assertTrue(session.loggedIn());
    assertEquals("test@example.com", session.email());
    assertFalse(session.githubLinked());
  }

  @Test
  public void testAuthenticatedSession_WithGithub() {
    when(identity.isAnonymous()).thenReturn(false);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("user@example.com");
    when(identity.getPrincipal()).thenReturn(principal);

    UserProfile profile =
        new UserProfile("user@example.com", "User Name", "user@example.com", null);
    UserProfile.GithubAccount gh =
        new UserProfile.GithubAccount("octocat", "url", "avatar", "123", java.time.Instant.now());
    profile.setGithub(gh);

    when(userProfileService.find("user@example.com")).thenReturn(Optional.of(profile));

    var session = sessionService.getCurrentSession();
    assertTrue(session.loggedIn());
    assertTrue(session.githubLinked());
    assertEquals("octocat", session.githubLogin());
  }
}
