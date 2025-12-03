package com.scanales.eventflow.public_.view;

import java.util.List;

public class CommunityViewModel {

  public String orgName;
  public String orgDescription;
  public long totalMembers;
  public long totalContributors;
  public long totalCommits;
  public long totalPullRequests;
  public long totalIssues;
  public List<TopContributor> topContributors;
  public List<CommunityActivity> recentActivity;

  public static CommunityViewModel mock() {
    CommunityViewModel vm = new CommunityViewModel();
    vm.orgName = "OSS Santiago";
    vm.orgDescription = "Open Source community building tools, platforms and events.";
    vm.totalMembers = 2400;
    vm.totalContributors = 42;
    vm.totalCommits = 12345;
    vm.totalPullRequests = 320;
    vm.totalIssues = 180;

    vm.topContributors =
        List.of(
            new TopContributor("scanalesespinoza", 120, 15, 8),
            new TopContributor("oss-member-1", 80, 10, 4),
            new TopContributor("oss-member-2", 60, 7, 2),
            new TopContributor("oss-member-3", 50, 5, 3),
            new TopContributor("oss-member-4", 40, 3, 1));

    vm.recentActivity =
        List.of(
            new CommunityActivity("scanalesespinoza", "opened pull request", "homedir", "2 hours ago"),
            new CommunityActivity("oss-member-1", "created issue", "eventflow", "5 hours ago"),
            new CommunityActivity("oss-member-2", "pushed commits", "arkit8s", "1 day ago"));

    return vm;
  }

  public static class TopContributor {
    public String username;
    public int commits;
    public int pullRequests;
    public int issues;

    public TopContributor(String username, int commits, int pullRequests, int issues) {
      this.username = username;
      this.commits = commits;
      this.pullRequests = pullRequests;
      this.issues = issues;
    }
  }

  public static class CommunityActivity {
    public String username;
    public String action;
    public String repo;
    public String when;

    public CommunityActivity(String username, String action, String repo, String when) {
      this.username = username;
      this.action = action;
      this.repo = repo;
      this.when = when;
    }
  }
}
