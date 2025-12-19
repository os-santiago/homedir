package com.scanales.eventflow.config;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle("i18n")
public interface AppMessages {

    @Message("My Profile · Homedir")
    String profile_title();

    @Message("Digital Profile")
    String profile_eyebrow();

    @Message("Manage your schedule, pick your favorite talks, and keep up with the community.")
    String profile_intro();

    @Message("Refresh")
    String btn_refresh();

    @Message("Logout")
    String btn_logout();

    @Message("View profile")
    String btn_view_profile();

    @Message("Link GitHub")
    String btn_link_github();

    @Message("Quest Board")
    String btn_quest_board();

    @Message("Save Class")
    String btn_save_class();

    @Message("Explore Community")
    String btn_explore_community();

    @Message("All")
    String btn_all();

    @Message("Attended")
    String btn_attended();

    @Message("Pending")
    String btn_pending();

    @Message("GitHub account linked successfully.")
    String msg_github_linked();

    @Message("Could not link GitHub ({0}). Please try again.")
    String msg_github_error(String error);

    @Message("Link GitHub to appear in Community and join with your user.")
    String msg_github_required();

    @Message("Not linked yet.")
    String msg_no_github();

    @Message("(Linked)")
    String msg_linked();

    @Message("Email")
    String label_email();

    @Message("Registered Talks")
    String label_registered_talks();

    @Message("Attended Talks")
    String label_attended_talks();

    @Message("Ratings")
    String label_ratings();

    @Message("Integrations")
    String section_integrations();

    @Message("GitHub")
    String section_gitHub();

    @Message("Resume")
    String section_resume();

    @Message("Progress")
    String section_progress();

    @Message("Identity")
    String section_identity();

    @Message("Community")
    String section_community();

    @Message("Agenda")
    String section_agenda();

    @Message("Settings")
    String section_settings();

    @Message("Level {0}")
    String resume_level(Object level);

    @Message("Experience: {0} XP / {1} XP")
    String resume_exp(int current, int total);

    @Message("Quest History")
    String resume_history();

    @Message("You haven't completed any quests yet.")
    String resume_no_history();

    @Message("Skill Tree")
    String progress_tree();

    @Message("Unlock new capabilities by leveling up.")
    String progress_intro();

    @Message("Novice")
    String progress_novice();

    @Message("Contributor")
    String progress_contributor();

    @Message("Mentor")
    String progress_mentor();

    @Message("Legend")
    String progress_legend();

    @Message("Your Guild (Quest Class)")
    String identity_guild();

    @Message("Choose your archetype so the community knows your main role.")
    String identity_intro();

    @Message("{0} active initiatives and open collaborations.")
    String community_initiatives(int count);

    @Message("You have added {0} talks to your schedule, attended {1} and rated {2}.")
    String agenda_intro(int added, long attended, long rated);

    @Message("You haven't added any talks yet. Explore events and add them to your schedule.")
    String agenda_no_talks();

    @Message("{0} days · {1} speakers")
    String agenda_days_speakers(int days, int speakers);

    @Message("Day {0}")
    String agenda_day(int day);

    @Message("Language")
    String settings_language();

    @Message("Select your preferred language.")
    String settings_language_intro();

    @Message("Save Preferences")
    String settings_save();

    @Message("Error saving changes")
    String msg_error_saving();

    @Message("Could not remove talk")
    String msg_error_removing();

    @Message("Are you sure?")
    String msg_confirm_delete();

    @Message("Reason...")
    String motivation_placeholder();

    @Message("Relevant to my work")
    String motivation_work();

    @Message("I want to learn about this")
    String motivation_learning();

    @Message("I know the speaker")
    String motivation_speaker();

    @Message("I found it interesting")
    String motivation_interesting();

    // --- Global Navigation ---
    @Message("Home")
    String nav_home();

    @Message("Community")
    String nav_community();

    @Message("Projects")
    String nav_projects();

    @Message("Events")
    String nav_events();

    @Message("Quests")
    String nav_quests();

    @Message("Login")
    String nav_login();

    @Message("My Profile")
    String nav_my_profile();

    @Message("Admin Panel")
    String nav_admin_panel();

    @Message("Connect GitHub")
    String nav_connect_github();

    @Message("Logout")
    String nav_logout();

    @Message("Signed in as")
    String nav_signed_in_as();

    // --- Home Page ---
    @Message("COMMUNITY PLATFORM TO SCALE YOUR OPEN SOURCE PROJECTS.")
    String home_hero_title();

    @Message("Get Started")
    String home_hero_start();

    @Message("Learn More")
    String home_hero_learn_more();

    // --- Quest Board ---
    @Message("Quest Board")
    String quest_board_title();

    @Message("Challenges & Rewards")
    String quest_board_eyebrow();

    @Message("Complete objectives, earn XP and unlock new ranks in the community. Quests sync with your GitHub activity.")
    String quest_board_intro();

    @Message("My Quests")
    String quest_filter_mine();

    @Message("Total: {0}")
    String quest_total(int count);

    @Message("No active quests")
    String quest_empty_title();

    @Message("It looks quiet for now.")
    String quest_empty_desc();

    @Message("Want to create your own quests?")
    String quest_empty_cta_text();

    @Message("Contribute on GitHub")
    String quest_empty_cta_btn();

    @Message("Rank {0}")
    String quest_rank(String difficulty);

    @Message("Start")
    String btn_start();

    @Message("Claim")
    String btn_claim();

    @Message("Continue")
    String btn_continue();

    @Message("View Details")
    String btn_view_details();

    @Message("Instructions")
    String btn_instructions();

    @Message("Closed")
    String btn_closed();

    @Message("IN PROGRESS")
    String badge_in_progress();

    @Message("COMPLETED")
    String badge_completed();
}
