package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.scanales.homedir.achievements.AchievementCatalog;
import com.scanales.homedir.achievements.AchievementCatalog.Achievement;
import com.scanales.homedir.achievements.AchievementCatalog.AchievementGuide;
import com.scanales.homedir.achievements.AchievementService;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.notifications.Notification;
import com.scanales.homedir.notifications.NotificationResult;
import com.scanales.homedir.notifications.NotificationService;
import com.scanales.homedir.notifications.NotificationType;
import com.scanales.homedir.service.UserProfileService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
class AchievementApiResourceTest {

  @InjectMock AchievementService achievementService;
  @InjectMock AchievementCatalog catalog;
  @InjectMock UserProfileService userProfileService;
  @InjectMock NotificationService notificationService;

  private static final String USER_ID = "user@example.com";
  private static final String ACHIEVEMENT_KEY = "first-pr";
  private static final String ACHIEVEMENT_TITLE = "First Pull Request";
  private static final String DISPLAY_NAME = "Test User";

  @BeforeEach
  void setUp() {
    reset(achievementService, catalog, userProfileService, notificationService);

    Achievement achievement =
        new Achievement(
            ACHIEVEMENT_KEY,
            ACHIEVEMENT_TITLE,
            "desc",
            "desc-es",
            "github",
            "https://docs.example.com",
            1,
            50,
            List.of(),
            List.of());
    AchievementGuide guide = new AchievementGuide(achievement, List.of());

    when(catalog.guideForKey(ACHIEVEMENT_KEY)).thenReturn(guide);
    when(achievementService.catalog()).thenReturn(catalog);

    UserProfile profile = new UserProfile();
    profile.setUserId(USER_ID);
    profile.setName(DISPLAY_NAME);
    when(userProfileService.find(USER_ID)).thenReturn(Optional.of(profile));

    when(notificationService.enqueue(any(Notification.class)))
        .thenReturn(NotificationResult.ACCEPTED_PERSISTED);
  }

  @Test
  @TestSecurity(user = USER_ID, roles = "user")
  void claimSuccessBroadcastsSocialNotificationWithUserNameAndAchievement() {
    when(achievementService.awardAchievementXp(USER_ID, ACHIEVEMENT_KEY)).thenReturn(true);

    given()
        .when()
        .post("/api/achievements/claim/" + ACHIEVEMENT_KEY)
        .then()
        .statusCode(200)
        .body("awarded", equalTo(true));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationService).enqueue(captor.capture());

    Notification n = captor.getValue();
    assert n.type == NotificationType.SOCIAL : "notification type should be SOCIAL, got " + n.type;
    assert n.message != null && n.message.contains(DISPLAY_NAME)
        : "message should contain user name, got: " + n.message;
    assert n.message.contains(ACHIEVEMENT_TITLE)
        : "message should contain achievement title, got: " + n.message;
  }

  @Test
  @TestSecurity(user = USER_ID, roles = "user")
  void claimFailureDoesNotBroadcast() {
    when(achievementService.awardAchievementXp(USER_ID, ACHIEVEMENT_KEY)).thenReturn(false);

    given()
        .when()
        .post("/api/achievements/claim/" + ACHIEVEMENT_KEY)
        .then()
        .statusCode(200)
        .body("awarded", equalTo(false));

    verify(notificationService, never()).enqueue(any(Notification.class));
  }
}
