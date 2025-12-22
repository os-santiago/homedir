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

    @Message("Level {level}")
    String resume_level(Object level);

    @Message("Experience: {current} XP / {total} XP")
    String resume_exp(int current, int total);

    @Message("{count} active initiatives and open collaborations.")
    String community_initiatives(int count);

    @Message("You have added {added} talks to your schedule, attended {attended} and rated {rated}.")
    String agenda_intro(int added, long attended, long rated);

    @Message("{days} days · {speakers} speakers")
    String agenda_days_speakers(int days, int speakers);

    @Message("Day {day}")
    String agenda_day(int day);

    @Message("Total: {count}")
    String quest_total(int count);

    @Message("Rank {difficulty}")
    String quest_rank(String difficulty);

    @Message("LEVEL {level}")
    String home_level(int level);

    @Message("{count} members")
    String meta_members(int count);

    @Message("{count} projects")
    String meta_projects(int count);

    @Message("Detail: {detail}")
    String community_error_detail(String detail);

    @Message("Contribution Score: {score}")
    String contribution_score(int score);

    // --- Events Page ---
    @Message("Events - HomeDir")
    String events_title();

    @Message("Agenda · HomeDir")
    String events_subtitle();

    @Message("Events and talks")
    String events_hero_title();

    @Message("Check the full schedule, past highlights and direct links for each edition.")
    String events_hero_desc();

    @Message("Upcoming")
    String events_card_upcoming();

    @Message("Scheduled")
    String events_card_upcoming_desc();

    @Message("Past")
    String events_card_past();

    @Message("References")
    String events_card_past_desc();

    @Message("Today")
    String events_card_today();

    @Message("Local timezone")
    String events_card_today_desc();

    @Message("Upcoming events")
    String events_section_upcoming_subtitle();

    @Message("Ongoing or starting soon")
    String events_section_upcoming_title();

    @Message("No upcoming events for now.")
    String events_empty_upcoming();

    @Message("History")
    String events_section_past_subtitle();

    @Message("Past events")
    String events_section_past_title();

    @Message("No past records yet.")
    String events_empty_past();

    // --- Projects Page ---
    @Message("Projects · HomeDir")
    String projects_title();

    @Message("Roadmap · HomeDir")
    String projects_subtitle();

    @Message("Active Projects")
    String projects_hero_title();

    @Message("Follow module status and go directly to the repository or section.")
    String projects_hero_desc();

    @Message("Open repository")
    String btn_open_repo();

    @Message("Connect GitHub to join")
    String btn_connect_join();

    @Message("Modules")
    String projects_section_modules();

    @Message("Visible roadmap")
    String projects_section_roadmap();

    @Message("Backend")
    String project_backend_eyebrow();

    @Message("Basic platform: authentication, profiles and module orchestration.")
    String project_backend_desc();

    @Message("In Production")
    String badge_production();

    @Message("Open")
    String btn_open();

    @Message("Realtime")
    String project_realtime_eyebrow();

    @Message("Global Notifications")
    String project_realtime_title();

    @Message("WebSocket channel and notification center for events and alerts.")
    String project_realtime_desc();

    @Message("Beta")
    String badge_beta();

    @Message("People")
    String project_people_eyebrow();

    @Message("Member directory and onboarding with GitHub to contribute.")
    String project_people_desc();

    @Message("In Design")
    String badge_design();

    @Message("You are browsing as a guest.")
    String home_guest_warning();

    @Message("Guest")
    String home_guest_name();

    @Message("Visitor")
    String home_guest_role();

    @Message("Contributions")
    String home_stat_contributions();

    @Message("Quests")
    String nav_quests();

    @Message("Events")
    String nav_events();

    @Message("Projects")
    String nav_projects();

    @Message("Home")
    String nav_home();

    @Message("Join Us")
    String home_btn_join();

    @Message("Propose Talk")
    String home_btn_propose();

    @Message("Community Village")
    String home_village_title();

    @Message("Connect with other developers, share knowledge and grow together.")
    String home_village_desc();

    @Message("Community Stats")
    String home_stats_title();

    @Message("Members")
    String home_stats_members();

    @Message("Total XP")
    String home_stats_xp();

    @Message("Quests Completed")
    String home_stats_quests();

    @Message("Active Projects")
    String home_stats_projects();

    @Message("Join Community")
    String btn_join_community();

    @Message("Community")
    String nav_community();

    @Message("Latest community activity")
    String home_community_activity();

    @Message("Join the community to collaborate and grow.")
    String home_community_desc();

    @Message("Explore")
    String home_btn_explore();

    @Message("Roster")
    String home_btn_roster();

    @Message("Check out upcoming events and talks.")
    String home_events_desc();

    @Message("Upcoming events")
    String home_events_activity();

    @Message("Attend")
    String home_btn_attend();

    @Message("Schedule")
    String home_btn_schedule();

    @Message("Contribute to open source projects.")
    String home_projects_desc();

    @Message("Project updates")
    String home_projects_activity();

    @Message("Platform by")
    String platform_by();

    @Message("The hub for developer communities")
    String platform_tagline();

    @Message("Member")
    String role_member();

    @Message("Visitor")
    String role_visitor();

    @Message("No talks found for this day.")
    String agenda_no_talks();

    @Message("Official Member")
    String badge_official_member();

    @Message("Completed")
    String badge_completed();

    @Message("In Progress")
    String badge_in_progress();

    @Message("Already a member")
    String btn_already_member();

    @Message("Claim Reward")
    String btn_claim();

    @Message("Closed")
    String btn_closed();

    @Message("Connect Now")
    String btn_connect_now();

    @Message("Explore Quests")
    String btn_explore_quests();

    @Message("Instructions")
    String btn_instructions();

    @Message("Join Guild")
    String btn_join_guild();

    @Message("Sign in with Google")
    String btn_login_google();

    @Message("Login or Join")
    String btn_login_join();

    @Message("Processing Request...")
    String btn_request_processing();

    @Message("Search")
    String btn_search();

    @Message("Start Quest")
    String btn_start();

    @Message("View Details")
    String btn_view_details();

    @Message("View Directory")
    String btn_view_directory();

    @Message("View PR")
    String btn_view_pr();

    @Message("Error Description")
    String community_error_desc();

    @Message("Error Title")
    String community_error_title();

    @Message("Community Hero Description")
    String community_hero_desc();

    @Message("Community Hero Title")
    String community_hero_title();

    @Message("Join our community to access exclusive content and events.")
    String community_join_card_desc();

    @Message("Join Us")
    String community_join_card_eyebrow();

    @Message("Become a Member")
    String community_join_card_title();

    @Message("You have successfully joined the community!")
    String community_joined_desc();

    @Message("Welcome!")
    String community_joined_title();

    @Message("Your account is now linked with GitHub.")
    String community_linked_desc();

    @Message("Account Linked")
    String community_linked_title();

    @Message("Meet our amazing community members.")
    String community_members_desc();

    @Message("Members")
    String community_members_eyebrow();

    @Message("Community Subtitle")
    String community_subtitle();

    @Message("Community Title")
    String community_title();

    @Message("Top Contributors")
    String community_top_contributors();

    @Message("No members found in directory.")
    String directory_empty();

    @Message("Member Directory")
    String directory_title();

    @Message("Alpha Repo")
    String header_alpha_repo();

    @Message("This is an alpha version. Things might break.")
    String header_alpha_text();

    @Message("Header Navigation")
    String header_aria_label();

    @Message("System is currently degraded.")
    String header_system_degraded();

    @Message("Hero Subtitle")
    String hero_subtitle();

    @Message("Guild Identity")
    String identity_guild();

    @Message("Identity Intro")
    String identity_intro();

    @Message("Interesting")
    String motivation_interesting();

    @Message("Learning")
    String motivation_learning();

    @Message("What is your motivation?")
    String motivation_placeholder();

    @Message("Speaker")
    String motivation_speaker();

    @Message("Work")
    String motivation_work();

    @Message("Are you sure you want to delete this?")
    String msg_confirm_delete();

    @Message("Error removing item.")
    String msg_error_removing();

    @Message("Error saving item.")
    String msg_error_saving();

    @Message("Admin Panel")
    String nav_admin_panel();

    @Message("Connect GitHub")
    String nav_connect_github();

    @Message("Login")
    String nav_login();

    @Message("Logout")
    String nav_logout();

    @Message("My Profile")
    String nav_my_profile();

    @Message("Notifications")
    String nav_notifications();

    @Message("Signed in as")
    String nav_signed_in_as();

    @Message("Contributor")
    String progress_contributor();

    @Message("Progress Intro")
    String progress_intro();

    @Message("Legend")
    String progress_legend();

    @Message("Mentor")
    String progress_mentor();

    @Message("Novice")
    String progress_novice();

    @Message("Progress Tree")
    String progress_tree();

    @Message("Public Description")
    String public_description();

    @Message("Public Title")
    String public_title();

    @Message("Quest Board")
    String quest_board_eyebrow();

    @Message("Complete quests to earn XP and badges.")
    String quest_board_intro();

    @Message("Quest Board")
    String quest_board_title();

    @Message("View Quests")
    String quest_empty_cta_btn();

    @Message("No quests available right now.")
    String quest_empty_cta_text();

    @Message("Check back later for new quests.")
    String quest_empty_desc();

    @Message("No Quests")
    String quest_empty_title();

    @Message("My Quests")
    String quest_filter_mine();

    @Message("Resume History")
    String resume_history();

    @Message("No history available.")
    String resume_no_history();

    @Message("Search")
    String search_aria_label();

    @Message("Search...")
    String search_placeholder();

    @Message("Language")
    String settings_language();

    @Message("Choose your preferred language.")
    String settings_language_intro();

    @Message("Save Settings")
    String settings_save();

    @Message("XP Progress")
    String xp_progress();
}
