package com.scanales.eventflow.config;

import io.quarkus.qute.i18n.Message;
import io.quarkus.qute.i18n.MessageBundle;

@MessageBundle("msg")
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
    String agenda_intro(int added, int attended, int rated);

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
}
