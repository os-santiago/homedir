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

    @Message("Join the Guild")
    String btn_join_guild();

    @Message("Explore Quests")
    String btn_explore_quests();

    @Message("Real Work")
    String hero_subtitle();

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

    // --- Header & Global ---
    @Message("Main Navigation")
    String header_aria_label();

    @Message("The community platform to scale your teams and projects")
    String platform_tagline();

    @Message("by OpenSourceSantiago")
    String platform_by();

    @Message("GitHub Repo")
    String header_alpha_repo();

    @Message("DevRel, OpenSource, InnerSource Community Platform")
    String header_alpha_text();

    @Message("Some features may be disabled.")
    String header_system_degraded();

    @Message("Notifications")
    String nav_notifications();

    // --- Home Page (Index) ---
    @Message("Modern toolbox for community building and open-source tech innovation.")
    String home_community_desc();

    @Message("Live teams, quests and adventures")
    String home_community_activity();

    @Message("Explore")
    String home_btn_explore();

    @Message("Roster")
    String home_btn_roster();

    @Message("Scale your squads with collaborative meetups, workshops and challenges.")
    String home_events_desc();

    @Message("Adaptive agenda, live streaming")
    String home_events_activity();

    @Message("Attend")
    String home_btn_attend();

    @Message("Schedule")
    String home_btn_schedule();

    @Message("Innovation hub for your open-source technology missions and squads.")
    String home_projects_desc();

    @Message("Fresh collaborations weekly")
    String home_projects_activity();

    @Message("Join")
    String home_btn_join();

    @Message("Propose")
    String home_btn_propose();

    @Message("Community campus")
    String home_village_title();

    @Message("Live rooms & quests")
    String home_village_desc();

    @Message("Community stats")
    String home_stats_title();

    @Message("Members online")
    String home_stats_members();

    @Message("Total XP")
    String home_stats_xp();

    @Message("Quests done")
    String home_stats_quests();

    @Message("Active projects")
    String home_stats_projects();

    @Message("You're playing as a NOVICE guest! Login to save your progress.")
    String home_guest_warning();

    @Message("NOVICE GUEST")
    String home_guest_name();

    @Message("VISITOR")
    String home_guest_role();

    @Message("LEVEL {0}")
    String home_level(int level);

    @Message("Contributions")
    String home_stat_contributions();

    @Message("{0} members")
    String meta_members(int count);

    @Message("{0} projects")
    String meta_projects(int count);

    @Message("Member")
    String role_member();

    @Message("Visitor")
    String role_visitor();

    // --- Public Page ---
    @Message("Public Page")
    String public_title();

    @Message("Everyone can see this page.")
    String public_description();

    @Message("Login with Google")
    String btn_login_google();

    // --- Community Page ---
    @Message("Community - HomeDir")
    String community_title();

    @Message("People · Community")
    String community_subtitle();

    @Message("Building Homedir together")
    String community_hero_title();

    @Message("Explore the member directory, connect with the community and join using your linked GitHub account.")
    String community_hero_desc();

    @Message("Error processing request")
    String community_error_title();

    @Message("There was a problem creating your Pull Request. The team has been notified.")
    String community_error_desc();

    @Message("Detail: {0}")
    String community_error_detail(String detail);

    @Message("Account linked!")
    String community_linked_title();

    @Message("Your GitHub account has been connected successfully. You can now join the community.")
    String community_linked_desc();

    @Message("Request Sent")
    String community_joined_title();

    @Message("A Pull Request has been created to add you to the directory.")
    String community_joined_desc();

    @Message("View Pull Request")
    String btn_view_pr();

    @Message("Join")
    String community_join_card_eyebrow();

    @Message("Link your GitHub")
    String community_join_card_title();

    @Message("To appear in the directory and earn XP.")
    String community_join_card_desc();

    @Message("Connect now")
    String btn_connect_now();

    @Message("Members")
    String community_members_eyebrow();

    @Message("Active in the community")
    String community_members_desc();

    @Message("Top Contributors")
    String community_top_contributors();

    @Message("Login and join")
    String btn_login_join();

    @Message("Request in process")
    String btn_request_processing();

    @Message("Join the community")
    String btn_join_community();

    @Message("Already a member")
    String btn_already_member();

    @Message("View directory")
    String btn_view_directory();

    @Message("Community Directory")
    String directory_title();

    @Message("Search...")
    String search_placeholder();

    @Message("Search member")
    String search_aria_label();

    @Message("Search")
    String btn_search();

    @Message("No visible members yet with this filter.")
    String directory_empty();

    @Message("Official Member")
    String badge_official_member();

    @Message("XP Progress")
    String xp_progress();

    @Message("Contribution Score: {0}")
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
}
