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

    @Message("Could not link GitHub ({error}). Please try again.")
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

    @Message("Date TBD")
    String events_meta_date_tba();

    @Message("{count} talks")
    String events_metric_talks(int count);

    @Message("{count} scenarios")
    String events_metric_scenarios(int count);

    @Message("Single-day")
    String events_feature_single_day();

    @Message("{days} days")
    String events_feature_multiday(int days);

    @Message("Tickets available")
    String events_feature_tickets();

    @Message("Location published")
    String events_feature_location();

    @Message("Official website")
    String events_feature_website();

    @Message("Social channels")
    String events_feature_social();

    @Message("Countdown")
    String events_countdown_title();

    @Message("Live now")
    String events_countdown_live();

    @Message("Starts today")
    String events_countdown_today();

    @Message("Starts tomorrow")
    String events_countdown_tomorrow();

    @Message("Starts in {days} days")
    String events_countdown_days(long days);

    @Message("Call for Papers")
    String events_cfp_open_cta();

    @Message("Call for Papers · {eventTitle}")
    String events_cfp_page_title(String eventTitle);

    @Message("Event not found · CFP")
    String events_cfp_not_found_title();

    @Message("Back to event")
    String events_cfp_back_to_event();

    @Message("Events · Community Speakers")
    String events_cfp_eyebrow();

    @Message("Submit your talk proposal")
    String events_cfp_heading();

    @Message("Share your talk idea for {eventTitle}. The organizing team will review it for the agenda.")
    String events_cfp_intro(String eventTitle);

    @Message("Proposal form")
    String events_cfp_submit_title();

    @Message("Keep it concise and practical. You can submit more than one proposal.")
    String events_cfp_submit_desc();

    @Message("Talk title")
    String events_cfp_form_title();

    @Message("Short summary")
    String events_cfp_form_summary();

    @Message("Abstract")
    String events_cfp_form_abstract();

    @Message("Level")
    String events_cfp_form_level();

    @Message("Format")
    String events_cfp_form_format();

    @Message("Duration (minutes)")
    String events_cfp_form_duration();

    @Message("Language")
    String events_cfp_form_language();

    @Message("Track")
    String events_cfp_form_track();

    @Message("Select an option")
    String events_cfp_form_select_placeholder();

    @Message("Tags (optional, comma separated)")
    String events_cfp_form_tags();

    @Message("Reference links (optional, comma separated)")
    String events_cfp_form_links();

    @Message("Send proposal")
    String events_cfp_form_submit();

    @Message("Login to submit a proposal for this event.")
    String events_cfp_login_required();

    @Message("Login")
    String events_cfp_login_btn();

    @Message("My proposals")
    String events_cfp_my_submissions_title();

    @Message("Refresh")
    String events_cfp_refresh();

    @Message("Loading your proposals...")
    String events_cfp_loading();

    @Message("Login to view your proposals.")
    String events_cfp_login_to_view();

    @Message("You have not submitted proposals yet.")
    String events_cfp_empty_mine();

    @Message("Could not load your proposals right now.")
    String events_cfp_error_load();

    @Message("Could not submit your proposal. Please try again.")
    String events_cfp_error_submit();

    @Message("Proposal submitted. It is now pending review.")
    String events_cfp_success_submit();

    @Message("Pending")
    String events_cfp_status_pending();

    @Message("Under review")
    String events_cfp_status_under_review();

    @Message("Accepted")
    String events_cfp_status_accepted();

    @Message("Rejected")
    String events_cfp_status_rejected();

    @Message("Withdrawn")
    String events_cfp_status_withdrawn();

    @Message("We could not find this event.")
    String events_cfp_not_found_desc();

    @Message("Auto-fill last proposal")
    String events_cfp_autofill_last();

    @Message("No saved proposal found.")
    String events_cfp_restore_empty();

    @Message("Last proposal restored.")
    String events_cfp_restore_success();

    @Message("Limit")
    String events_cfp_quota_prefix();

    @Message("available slots")
    String events_cfp_quota_remaining();

    @Message("You reached the maximum proposals for this event.")
    String events_cfp_error_limit_reached();

    @Message("You already submitted a proposal with the same title.")
    String events_cfp_error_duplicate_title();

    @Message("Delete")
    String events_cfp_delete();

    @Message("Are you sure you want to delete this proposal?")
    String events_cfp_confirm_delete();

    @Message("Proposal deleted.")
    String events_cfp_success_delete();

    @Message("Could not delete the proposal.")
    String events_cfp_error_delete();

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

    @Message("Project")
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

    @Message("Community Curated · Homedir")
    String community_page_title();

    @Message("Community · Collaborative curation")
    String community_page_eyebrow();

    @Message("Community-recommended content")
    String community_page_heading();

    @Message("Find what matters faster: featured picks, fresh content, and topic filters.")
    String community_page_intro();

    @Message("HomeDir users")
    String community_pulse_homedir_users();

    @Message("GitHub users")
    String community_pulse_github_users();

    @Message("Discord users")
    String community_pulse_discord_users();

    @Message("Curated picks")
    String community_pulse_curated_picks();

    @Message("Featured")
    String community_view_featured();

    @Message("New")
    String community_view_new();

    @Message("All")
    String community_filter_all();

    @Message("Internet")
    String community_filter_internet();

    @Message("Members")
    String community_filter_members();

    @Message("All")
    String community_topic_all();

    @Message("Clear filters")
    String community_clear_filters();

    @Message("Quick interests")
    String community_interest_title();

    @Message("Pick a topic and jump to relevant content instantly.")
    String community_interest_desc();

    @Message("Tag radar")
    String community_radar_title();

    @Message("Filter by popular tags in the current view.")
    String community_radar_desc();

    @Message("Hot now")
    String community_hot_title();

    @Message("Top voted this week")
    String community_hot_desc();

    @Message("No curated content is available right now.")
    String community_empty_no_content();

    @Message("Load more")
    String community_load_more();

    @Message("Community · Feed")
    String community_propose_eyebrow();

    @Message("Propose content")
    String community_propose_title();

    @Message("Share a relevant resource and the team will review it before publishing.")
    String community_propose_intro();

    @Message("Title")
    String community_form_title();

    @Message("Ex: Kubernetes 1.35 updates for platform teams")
    String community_form_title_placeholder();

    @Message("URL")
    String community_form_url();

    @Message("https://...")
    String community_form_url_placeholder();

    @Message("Summary")
    String community_form_summary();

    @Message("1-3 lines with the most relevant context")
    String community_form_summary_placeholder();

    @Message("Source (optional)")
    String community_form_source();

    @Message("Ex: CNCF Blog")
    String community_form_source_placeholder();

    @Message("Tags (optional, comma-separated)")
    String community_form_tags();

    @Message("ai, opensource, platform engineering")
    String community_form_tags_placeholder();

    @Message("Send proposal")
    String community_form_submit();

    @Message("My proposals")
    String community_my_submissions_title();

    @Message("You have not sent any proposals yet.")
    String community_my_submissions_empty();

    @Message("Sign in to propose content to the Community Feed.")
    String community_login_to_propose();

    @Message("Sign in")
    String community_login();

    @Message("Community · Moderation")
    String community_moderation_eyebrow();

    @Message("Moderation queue")
    String community_moderation_title();

    @Message("Review pending proposals and publish them to the official feed.")
    String community_moderation_intro();

    @Message("No proposals pending moderation.")
    String community_moderation_empty();

    @Message("Only admins can moderate proposals.")
    String community_moderation_admin_only();

    @Message("Community Picks")
    String community_submenu_picks();

    @Message("Propose Content")
    String community_submenu_propose();

    @Message("Community Board")
    String community_submenu_board();

    @Message("Moderation")
    String community_submenu_moderation();

    @Message("No matches for current filters. Try a different combination.")
    String community_js_empty_filtered();

    @Message("No curated content is available right now.")
    String community_js_empty_generic();

    @Message("items")
    String community_js_items_unit();

    @Message("Score")
    String community_js_score_prefix();

    @Message("{count} items")
    String community_js_items_count(int count);

    @Message("Score {score}")
    String community_js_score_label(String score);

    @Message("Sign in to vote")
    String community_js_vote_login_required();

    @Message("Recommended")
    String community_js_vote_recommended();

    @Message("Must see")
    String community_js_vote_must_see();

    @Message("Not for me")
    String community_js_vote_not_for_me();

    @Message("Could not load community content.")
    String community_js_load_error();

    @Message("Could not register your vote. Please try again.")
    String community_js_vote_error();

    @Message("All tags")
    String community_js_all_tags();

    @Message("Source: Internet")
    String community_js_filter_source_internet();

    @Message("Source: Members")
    String community_js_filter_source_members();

    @Message("Topic")
    String community_js_filter_topic_prefix();

    @Message("Tag")
    String community_js_filter_tag_prefix();

    @Message("Internet")
    String community_js_origin_internet();

    @Message("Members")
    String community_js_origin_members();

    @Message("min")
    String community_js_read_unit();

    @Message("Topic: {topic}")
    String community_js_filter_topic(String topic);

    @Message("Tag: #{tag}")
    String community_js_filter_tag(String tag);

    @Message("New")
    String community_js_badge_new();

    @Message("Top {rank}")
    String community_js_top_rank(int rank);

    @Message("Top")
    String community_js_top_prefix();

    @Message("Show summary")
    String community_js_summary_show();

    @Message("Show less")
    String community_js_summary_hide();

    @Message("Pending")
    String community_submissions_status_pending();

    @Message("Approved")
    String community_submissions_status_approved();

    @Message("Rejected")
    String community_submissions_status_rejected();

    @Message("Community member")
    String community_submissions_source_fallback();

    @Message("Open source")
    String community_submissions_open_source();

    @Message("Optional moderation note")
    String community_submissions_note_placeholder();

    @Message("Approve")
    String community_submissions_approve();

    @Message("Reject")
    String community_submissions_reject();

    @Message("Proposal approved and published.")
    String community_submissions_feedback_approved();

    @Message("Proposal rejected.")
    String community_submissions_feedback_rejected();

    @Message("Could not moderate this proposal.")
    String community_submissions_error_moderate();

    @Message("Could not load your submissions.")
    String community_submissions_error_load_mine();

    @Message("Could not load moderation queue.")
    String community_submissions_error_load_moderation();

    @Message("Only admins can moderate proposals.")
    String community_submissions_error_admin_only();

    @Message("The proposal no longer exists.")
    String community_submissions_error_not_found();

    @Message("This URL already exists in the curated feed.")
    String community_submissions_error_conflict();

    @Message("Could not publish content right now. Please try again in a few minutes.")
    String community_submissions_error_unavailable();

    @Message("Could not process moderation.")
    String community_submissions_error_generic_moderation();

    @Message("You reached the daily proposal limit.")
    String community_submissions_error_daily_limit();

    @Message("A proposal already exists for that URL.")
    String community_submissions_error_duplicate_url();

    @Message("Invalid data")
    String community_submissions_error_invalid_data_prefix();

    @Message("Could not send your proposal.")
    String community_submissions_error_submit();

    @Message("Proposal sent. It is now pending review.")
    String community_submissions_feedback_submitted();

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

    @Message("Public Profile CTA Title")
    String public_profile_cta_title();

    @Message("Public Profile CTA Description")
    String public_profile_cta_desc();

    @Message("Public Profile CTA Button")
    String public_profile_cta_btn();

    @Message("Share Profile")
    String btn_share_profile();

    @Message("XP Progress")
    String xp_progress();

    // --- Home Page Refactor ---
    @Message("Popular Events")
    String home_popular_events_title();

    @Message("View All")
    String home_view_all_events();

    @Message("No upcoming events at the moment.")
    String home_no_events();

    @Message("Browse Past Events")
    String home_browse_past_events();

    @Message("Top Contributors")
    String home_top_contributors_title();

    @Message("Heroes of the homedir repository")
    String home_contributors_subtitle();

    @Message("Unable to load contributors.")
    String home_no_contributors();

    @Message("Want to appear here?")
    String home_join_community_text();

    @Message("Contribute on GitHub")
    String home_contribute_btn();

    @Message("Retro Platform · OSS Santiago Community")
    String home_eyebrow();

    @Message("by OpenSourceSantiago — Community platform to scale your open source teams and projects.")
    String home_tagline();

    @Message("Social")
    String home_community_title();

    @Message("Discover curated community content.")
    String home_community_card_desc();

    @Message("Events")
    String home_events_title();

    @Message("Upcoming meetups and gatherings.")
    String home_events_card_desc();

    @Message("Project")
    String home_projects_title();

    @Message("Collaborate on open source projects.")
    String home_projects_card_desc();

    // Home Page Highlights
    @Message("Social")
    String home_pill_social();

    @Message("Events")
    String home_pill_events();

    @Message("Project")
    String home_pill_project();

    @Message("Social · Events · Project")
    String home_hero_title_combined();

    @Message("One-page highlights to quickly discover what is new in the Community platform for OSS Santiago.")
    String home_hero_desc();

    @Message("Welcome")
    String home_welcome_title();

    @Message("HomeDir: your community to build, learn, and share.")
    String home_welcome_en();

    @Message("HomeDir: tu comunidad para construir, aprender y compartir.")
    String home_welcome_es();

    @Message("Community Highlights")
    String home_highlights_intro();

    @Message("updates available")
    String home_metric_updates();

    @Message("upcoming events")
    String home_metric_upcoming();

    @Message("active contributors")
    String home_metric_contributors();

    @Message("Social highlights")
    String home_social_highlights_eyebrow();

    @Message("Latest community content")
    String home_social_highlights_title();

    @Message("Open")
    String home_btn_open_social();

    @Message("No social updates loaded yet. Open Social to check the latest refresh.")
    String home_social_empty();

    @Message("Events highlights")
    String home_events_highlights_eyebrow();

    @Message("Upcoming agenda")
    String home_events_highlights_title();

    @Message("Open")
    String home_btn_open_events();

    @Message("No upcoming events yet. Visit Events for historical sessions.")
    String home_events_empty();

    @Message("Date TBA")
    String home_event_date_tba();

    @Message("Open the event for full details.")
    String home_event_desc_default();

    @Message("Project highlights")
    String home_project_highlights_eyebrow();

    @Message("Build with the core contributors")
    String home_project_highlights_title();

    @Message("Open")
    String home_btn_open_project();

    @Message("total contributions")
    String home_project_total_contributions();

    @Message("Latest release")
    String home_project_release_label();

    @Message("Latest commit")
    String home_project_commit_label();

    @Message("contribs")
    String home_project_contribs_unit();

    @Message("Top contributors")
    String home_project_top_contributors();

    @Message("No contributor data available at the moment.")
    String home_project_no_data();

    @Message("Ready to contribute")
    String home_project_actions_chip();

    @Message("Pick a path and start")
    String home_project_actions_title();

    @Message("Explore project tracks, choose an issue, and contribute through the shared workflow.")
    String home_project_actions_desc();

    @Message("Explore tracks")
    String home_btn_explore_tracks();

    @Message("View repository")
    String home_btn_view_repo();

}

