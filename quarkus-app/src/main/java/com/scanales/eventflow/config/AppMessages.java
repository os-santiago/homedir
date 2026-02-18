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

    @Message("Submitting...")
    String events_cfp_submitting_title();

    @Message("Please wait, we are sending your proposal.")
    String events_cfp_submitting_desc();

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

    @Message("New Proposal")
    String events_cfp_tab_new_proposal();

    @Message("My Proposals")
    String events_cfp_tab_my_proposals();

    @Message("proposals")
    String events_cfp_proposals_unit();

    @Message("Submitted and waiting for moderator review.")
    String events_cfp_status_desc_pending();

    @Message("The event team is currently reviewing your proposal.")
    String events_cfp_status_desc_under_review();

    @Message("Selected for the event agenda.")
    String events_cfp_status_desc_accepted();

    @Message("Not selected for this edition.")
    String events_cfp_status_desc_rejected();

    @Message("Testing")
    String events_cfp_testing_badge();

    @Message("Testing mode")
    String events_cfp_testing_title();

    @Message("This CFP flow is currently in testing mode. Submissions are considered test data and are not part of a formal process for this event.")
    String events_cfp_testing_desc();

    @Message("CFP testing notice")
    String events_cfp_testing_aria();

    @Message("CFP: testing mode")
    String events_cfp_admin_testing_title();

    @Message("Toggle a public warning on the CFP submission form so attendees know this is a test flow.")
    String events_cfp_admin_testing_desc();

    @Message("Show testing notice")
    String events_cfp_admin_testing_toggle_label();

    @Message("Save")
    String events_cfp_admin_testing_save();

    @Message("Saved.")
    String events_cfp_admin_testing_saved();

    @Message("Could not update testing mode right now.")
    String events_cfp_admin_testing_error();

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

    // --- Project Dashboard (/proyectos) ---
    @Message("Project · Homedir")
    String project_dashboard_title();

    @Message("Product delivery overview")
    String project_dashboard_hero_title();

    @Message("One place to track real status, active features, releases, and delivery signals for homedir.")
    String project_dashboard_tagline();

    @Message("Repository")
    String project_dashboard_btn_repository();

    @Message("Releases")
    String project_dashboard_btn_releases();

    @Message("Issues")
    String project_dashboard_btn_issues();

    @Message("Stars")
    String project_dashboard_metric_stars();

    @Message("Community interest signal")
    String project_dashboard_metric_stars_note();

    @Message("Forks")
    String project_dashboard_metric_forks();

    @Message("Active collaboration branches")
    String project_dashboard_metric_forks_note();

    @Message("Issues")
    String project_dashboard_metric_issues();

    @Message("Current open backlog")
    String project_dashboard_metric_issues_note();

    @Message("Contributors")
    String project_dashboard_metric_contributors();

    @Message("Contributions: {total}")
    String project_dashboard_metric_contributions(int total);

    @Message("Latest push")
    String project_dashboard_metric_last_push();

    @Message("Recent code activity")
    String project_dashboard_metric_last_push_note();

    @Message("Latest release")
    String project_dashboard_metric_latest_release();

    @Message("Progress")
    String project_dashboard_progress_subtitle();

    @Message("Implementation status")
    String project_dashboard_progress_title();

    @Message("LIVE")
    String project_dashboard_status_live();

    @Message("BETA")
    String project_dashboard_status_beta();

    @Message("NEXT")
    String project_dashboard_status_next();

    @Message("Snapshot updated {ago}")
    String project_dashboard_snapshot_updated(String ago);

    @Message("Releases")
    String project_dashboard_releases_subtitle();

    @Message("Recent history")
    String project_dashboard_releases_title();

    @Message("View all")
    String project_dashboard_view_all();

    @Message("No published releases at this moment.")
    String project_dashboard_empty_releases();

    @Message("Features")
    String project_dashboard_features_subtitle();

    @Message("Homedir feature map")
    String project_dashboard_features_title();

    @Message("Open")
    String project_dashboard_open();

    @Message("Team")
    String project_dashboard_team_subtitle();

    @Message("Top contributors")
    String project_dashboard_team_title();

    @Message("Contributor data is not available yet.")
    String project_dashboard_empty_contributors();

    @Message("contribs")
    String project_dashboard_contribs_unit();

    @Message("No published releases")
    String project_dashboard_latest_release_none();

    @Message("Release cadence")
    String project_dashboard_highlight_release_title();

    @Message("No recent releases")
    String project_dashboard_highlight_release_none();

    @Message("{count} recent releases")
    String project_dashboard_highlight_release_count(int count);

    @Message("{ago} since the latest publication")
    String project_dashboard_highlight_release_note(String ago);

    @Message("Backlog health")
    String project_dashboard_highlight_backlog_title();

    @Message("{count} open issues")
    String project_dashboard_highlight_backlog_open_issues(int count);

    @Message("Tracked in GitHub Issues")
    String project_dashboard_highlight_backlog_note();

    @Message("Code activity")
    String project_dashboard_highlight_activity_title();

    @Message("Latest push in the main repository")
    String project_dashboard_highlight_activity_note();

    @Message("Curated Community Feed")
    String project_dashboard_feature_community_feed_title();

    @Message("File-based ingest with ranking, 3-state votes, and weekly highlights.")
    String project_dashboard_feature_community_feed_desc();

    @Message("Events Persistence")
    String project_dashboard_feature_events_persistence_title();

    @Message("Events persist across restarts and new deployments.")
    String project_dashboard_feature_events_persistence_desc();

    @Message("One-page Home Highlights")
    String project_dashboard_feature_home_highlights_title();

    @Message("Compact home with Community, Events and Project in one quick view.")
    String project_dashboard_feature_home_highlights_desc();

    @Message("Global Notifications")
    String project_dashboard_feature_global_notifications_title();

    @Message("Real-time global notifications with WebSocket and a unified center.")
    String project_dashboard_feature_global_notifications_desc();

    @Message("Contributor Telemetry Cache")
    String project_dashboard_feature_contributor_cache_title();

    @Message("Contributor metrics cached to avoid per-request integrations.")
    String project_dashboard_feature_contributor_cache_desc();

    @Message("ADev Practitioner Playbook")
    String project_dashboard_feature_adev_playbook_title();

    @Message("Formalize the ADev baseline as operating guidance for upcoming cycles.")
    String project_dashboard_feature_adev_playbook_desc();

    @Message("n/a")
    String project_dashboard_relative_na();

    @Message("now")
    String project_dashboard_relative_now();

    @Message("{minutes}m ago")
    String project_dashboard_relative_minutes(long minutes);

    @Message("{hours}h ago")
    String project_dashboard_relative_hours(long hours);

    @Message("{days}d ago")
    String project_dashboard_relative_days(long days);

    @Message("{months}mo ago")
    String project_dashboard_relative_months(long months);

    @Message("{years}y ago")
    String project_dashboard_relative_years(long years);

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

    @Message("Community submenu")
    String community_submenu_aria_label();

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

    @Message("Notifications Center")
    String notifications_center_title();

    @Message("Here you can see global alerts about updates in available event schedules. Mark as read or remove notifications you no longer need.")
    String notifications_center_subtitle();

    @Message("All")
    String notifications_center_filter_all();

    @Message("Unread")
    String notifications_center_filter_unread();

    @Message("Select all")
    String notifications_center_action_select_all();

    @Message("Mark all as read")
    String notifications_center_action_mark_all_read();

    @Message("Delete selected")
    String notifications_center_action_delete_selected();

    @Message("Delete all")
    String notifications_center_action_delete_all();

    @Message("No notifications for now.")
    String notifications_center_empty();

    @Message("Delete all?")
    String notifications_center_confirm_title();

    @Message("Are you sure you want to delete all received notifications?")
    String notifications_center_confirm_desc();

    @Message("Confirm")
    String notifications_center_confirm_btn();

    @Message("Cancel")
    String notifications_center_cancel_btn();

    @Message("Mark as read")
    String notifications_center_js_toggle_mark_read();

    @Message("Mark as unread")
    String notifications_center_js_toggle_mark_unread();

    @Message("View talk")
    String notifications_center_js_link_view_talk();

    @Message("Open")
    String notifications_center_js_link_open();

    @Message("Notification")
    String notifications_center_js_default_title();

    @Message("Event")
    String notifications_center_js_category_event();

    @Message("Talk")
    String notifications_center_js_category_talk();

    @Message("Break")
    String notifications_center_js_category_break();

    @Message("Announcement")
    String notifications_center_js_category_announcement();

    @Message("Select all")
    String notifications_center_js_select_all();

    @Message("Deselect all")
    String notifications_center_js_deselect_all();

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

    // --- Site I18n Hardcoded Migration ---
    @Message("HomeDir · OSSantiago Community Platform")
    String footer_platform_line();

    @Message("Built with Quarkus · Qute · Open Source")
    String footer_built_with_line();

    @Message("About / Version")
    String footer_about_version();

    @Message("Close login")
    String login_modal_close_aria();

    @Message("Welcome to HomeDir")
    String login_modal_title();

    @Message("Login to manage your profile and data")
    String login_modal_subtitle();

    @Message("Continue with Google")
    String login_modal_google();

    @Message("Continue with GitHub")
    String login_modal_github();

    @Message("Skip to main content")
    String layout_skip_main_content();

    @Message("Breadcrumbs")
    String layout_breadcrumbs_aria();

    @Message("Avatar")
    String header_avatar_alt();

    @Message("LV")
    String header_level_short();

    @Message("LVL {level}")
    String header_level_label(Object level);

    @Message("{current} / {total} XP")
    String header_xp_progress(int current, int total);

    @Message("GitHub: @{login}")
    String header_github_connected(String login);

    @Message("Happening now")
    String now_box_title();

    @Message("Latest, live, and next by event.")
    String now_box_subtitle();

    @Message("View full agenda")
    String now_box_view_full_agenda();

    @Message("Finished")
    String now_box_status_finished();

    @Message("Live")
    String now_box_status_live();

    @Message("Next")
    String now_box_status_next();

    @Message("HomeDir - Community Platform")
    String layout_default_title();

    @Message("HomeDir - Community Platform")
    String layout_default_og_title();

    @Message("Join the Open Source Santiago community platform. Level up your skills, join quests, and showcase your profile.")
    String layout_default_og_description();

    @Message("HomeDir")
    String layout_default_twitter_title();

    @Message("Join the Open Source Santiago community platform.")
    String layout_default_twitter_description();

    @Message("Event not found - Homedir")
    String events_detail_not_found_page_title();

    @Message("Back to Events")
    String events_detail_back_to_events();

    @Message("Live")
    String events_detail_badge_ongoing();

    @Message("hrs")
    String events_detail_hours_suffix();

    @Message("View venue map")
    String events_detail_view_map();

    @Message("Logo of {title}")
    String events_detail_logo_alt(String title);

    @Message("Duration: {days} day")
    String events_detail_duration_day(int days);

    @Message("Duration: {days} days")
    String events_detail_duration_days(int days);

    @Message("Tickets")
    String events_detail_tickets();

    @Message("Website")
    String events_detail_web();

    @Message("Scenarios")
    String events_detail_scenarios_title();

    @Message("View talks")
    String events_detail_view_talks();

    @Message("Agenda")
    String events_detail_agenda_title();

    @Message("Sequential")
    String events_detail_agenda_sequential();

    @Message("By Scenario")
    String events_detail_agenda_by_scenario();

    @Message("Day {day}")
    String events_detail_day_label(int day);

    @Message("Time")
    String events_detail_table_time();

    @Message("Activity")
    String events_detail_table_activity();

    @Message("Speakers")
    String events_detail_table_speakers();

    @Message("Scenario")
    String events_detail_table_scenario();

    @Message("Event not found")
    String events_detail_not_found_title();

    @Message("Sorry, we could not find the event you are looking for.")
    String events_detail_not_found_desc();

    @Message("Explore other events")
    String events_detail_not_found_cta();

    @Message("Talk")
    String talk_title_default();

    @Message("Home")
    String talk_breadcrumb_home();

    @Message("Talk: {talkName}")
    String talk_breadcrumb_label(String talkName);

    @Message("Day {day}")
    String talk_chip_day(int day);

    @Message("Talk in preparation. It will be available soon.")
    String talk_preparing_message();

    @Message("Speaker")
    String talk_label_speaker();

    @Message("Co-Speaker")
    String talk_label_co_speaker();

    @Message("Schedule")
    String talk_label_schedule();

    @Message("TBD")
    String talk_tbd();

    @Message("Location")
    String talk_label_location();

    @Message("Duration")
    String talk_label_duration();

    @Message("{minutes} min")
    String talk_duration_minutes(int minutes);

    @Message("View in my profile")
    String talk_btn_view_profile();

    @Message("Add to my talks")
    String talk_btn_add_to_profile();

    @Message("Added to my profile")
    String talk_js_added();

    @Message("This talk was already in your profile.")
    String talk_js_exists();

    @Message("You must log in to add this talk.")
    String talk_js_login_required();

    @Message("An error occurred while registering this talk.")
    String talk_js_error();

    @Message("Schedules")
    String talk_schedule_slots();

    @Message("Day {day} - {startTime} ({durationMinutes} min)")
    String talk_occurrence_line(int day, String startTime, int durationMinutes);

    @Message("Location TBD")
    String talk_location_tbd();

    @Message("Back to scenario")
    String talk_back_to_scenario();

    @Message("Back to event")
    String talk_back_to_event();

    @Message("Talk not found.")
    String talk_not_found();

    @Message("Community Board · Homedir")
    String community_board_page_title();

    @Message("Community · Board")
    String community_board_subtitle();

    @Message("Community Board")
    String community_board_heading();

    @Message("Who participates, where they collaborate from, and how they share their community presence.")
    String community_board_intro();

    @Message("HomeDir users")
    String community_board_group_homedir();

    @Message("People who signed in with Google and created a Homedir account.")
    String community_board_group_homedir_desc();

    @Message("GitHub users")
    String community_board_group_github();

    @Message("Contributors with a linked GitHub account in the OSSantiago ecosystem.")
    String community_board_group_github_desc();

    @Message("Discord users")
    String community_board_group_discord();

    @Message("People who joined our official OSSantiago Discord server.")
    String community_board_group_discord_desc();

    @Message("Listed profiles: {count}")
    String community_board_discord_listed(int count);

    @Message("Online now: {count}")
    String community_board_discord_online_now(int count);

    @Message("Data source: {source}")
    String community_board_discord_source(String source);

    @Message("Last sync: {timestamp}")
    String community_board_discord_last_sync(String timestamp);

    @Message("Discord API (bot token)")
    String community_board_discord_source_bot_api();

    @Message("Discord API (guild preview)")
    String community_board_discord_source_preview_api();

    @Message("Discord API (server widget)")
    String community_board_discord_source_widget_api();

    @Message("Community board file")
    String community_board_discord_source_file();

    @Message("Unavailable")
    String community_board_discord_source_unavailable();

    @Message("Missing configuration")
    String community_board_discord_source_misconfigured();

    @Message("Integration disabled")
    String community_board_discord_source_disabled();

    @Message("View members")
    String community_board_view_members();

    @Message("{count} members")
    String community_board_members_count(int count);

    @Message("Search by name, handle or email")
    String community_board_search_placeholder();

    @Message("Search member")
    String community_board_search_aria();

    @Message("Search")
    String community_board_search();

    @Message("No members found with current filter.")
    String community_board_no_members();

    @Message("Member since {date}")
    String community_board_member_since(String date);

    @Message("Open")
    String community_board_open();

    @Message("Copy profile link")
    String community_board_copy_profile_link();

    @Message("Link copied")
    String community_board_link_copied();

    @Message("Copy failed")
    String community_board_copy_failed();

}
