package com.scanales.eventflow.model;

public enum GamificationActivity {
  FIRST_LOGIN_BONUS("first_login_bonus", 100, QuestClass.MAGE, false, true, "Joined HomeDir"),
  DAILY_CHECKIN("daily_checkin", 10, QuestClass.MAGE, true, false, "Daily check-in"),
  HOME_VIEW("home_view", 4, QuestClass.MAGE, true, false, "Home highlights review"),
  PROFILE_VIEW("profile_view", 3, QuestClass.MAGE, true, false, "Profile dashboard review"),
  PUBLIC_PROFILE_VIEW("public_profile_view", 2, QuestClass.SCIENTIST, true, false, "Public profile discovery"),
  GITHUB_LINKED("github_linked", 25, QuestClass.ENGINEER, false, true, "Linked GitHub account"),
  DISCORD_LINKED("discord_linked", 25, QuestClass.MAGE, false, true, "Linked Discord account"),
  COMMUNITY_MAIN_VIEW("community_main_view", 4, QuestClass.SCIENTIST, true, false, "Community main review"),
  COMMUNITY_PICKS_VIEW("community_picks_view", 5, QuestClass.SCIENTIST, true, false, "Community picks review"),
  COMMUNITY_PROPOSE_VIEW("community_propose_view", 5, QuestClass.SCIENTIST, true, false, "Community propose review"),
  COMMUNITY_BOARD_VIEW("community_board_view", 4, QuestClass.SCIENTIST, true, false, "Community board review"),
  COMMUNITY_BOARD_MEMBERS_VIEW(
      "community_board_members_view", 5, QuestClass.SCIENTIST, true, false, "Community members review"),
  COMMUNITY_VOTE("community_vote", 5, QuestClass.SCIENTIST, false, false, "Community vote"),
  COMMUNITY_REVIEW("community_review", 2, QuestClass.SCIENTIST, true, false, "Community feed review"),
  COMMUNITY_SUBMISSION(
      "community_submission", 20, QuestClass.SCIENTIST, false, false, "Community content proposal"),
  COMMUNITY_SUBMISSION_APPROVED(
      "community_submission_approved",
      40,
      QuestClass.SCIENTIST,
      false,
      false,
      "Community proposal approved"),
  WARRIOR_EVENTS_EXPLORATION(
      "warrior_events_exploration",
      4,
      QuestClass.WARRIOR,
      true,
      false,
      "Events exploration mission"),
  EVENT_DIRECTORY_VIEW("event_directory_view", 4, QuestClass.ENGINEER, true, false, "Events directory exploration"),
  EVENT_VIEW("event_view", 3, QuestClass.ENGINEER, true, false, "Event exploration"),
  PROJECT_VIEW("project_view", 3, QuestClass.ENGINEER, true, false, "Project exploration"),
  BOARD_PROFILE_OPEN("board_profile_open", 4, QuestClass.SCIENTIST, true, false, "Community board profile open"),
  TALK_VIEW("talk_view", 5, QuestClass.WARRIOR, true, false, "Talk exploration"),
  AGENDA_VIEW("agenda_view", 4, QuestClass.WARRIOR, true, false, "Agenda exploration"),
  CFP_SUBMIT("cfp_submit", 30, QuestClass.WARRIOR, false, false, "CFP submission"),
  CFP_ACCEPTED("cfp_accepted", 40, QuestClass.WARRIOR, false, false, "CFP accepted"),
  SESSION_EVALUATION("session_evaluation", 12, QuestClass.WARRIOR, false, false, "Session evaluation");

  private final String key;
  private final int xp;
  private final QuestClass questClass;
  private final boolean oncePerDay;
  private final boolean onceEver;
  private final String title;

  GamificationActivity(
      String key, int xp, QuestClass questClass, boolean oncePerDay, boolean onceEver, String title) {
    this.key = key;
    this.xp = xp;
    this.questClass = questClass;
    this.oncePerDay = oncePerDay;
    this.onceEver = onceEver;
    this.title = title;
  }

  public String key() {
    return key;
  }

  public int xp() {
    return xp;
  }

  public QuestClass questClass() {
    return questClass;
  }

  public boolean oncePerDay() {
    return oncePerDay;
  }

  public boolean onceEver() {
    return onceEver;
  }

  public String title() {
    return title;
  }
}
