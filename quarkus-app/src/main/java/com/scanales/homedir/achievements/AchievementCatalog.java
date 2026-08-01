package com.scanales.homedir.achievements;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Static catalog of GitHub achievements the os-santiago community can work toward, together with
 * the org repositories that help earn each one and the verification criteria used to measure
 * progress via the GitHub API.
 *
 * <p>The catalog mirrors the achievements listed in issue #1043 and the drknzz/GitHub-Achievements
 * reference. Thresholds are based on the minimum tier for each achievement.
 */
@ApplicationScoped
public class AchievementCatalog {

  /** A repository in the os-santiago org that helps earn one or more achievements. */
  public record OrgRepo(String name, String url, String usage) {}

  /** Maps an achievement to the org repo(s) that help earn it. */
  public record AchievementGuide(Achievement achievement, List<OrgRepo> repos) {}

  private final List<AchievementGuide> guides;
  private final List<OrgRepo> orgRepos;

  public AchievementCatalog() {
    this.orgRepos =
        List.of(
            new OrgRepo(
                "os-santiago/homedir",
                "https://github.com/os-santiago/homedir",
                "PRs, issues, co-authoring (Pull Shark, YOLO, Quickdraw, Pair Extraordinaire)"),
            new OrgRepo(
                "os-santiago/demo-repository",
                "https://github.com/os-santiago/demo-repository",
                "Sandbox for achievement practice and testing"),
            new OrgRepo(
                "os-santiago/os-santiago.github.io",
                "https://github.com/os-santiago/os-santiago.github.io",
                "Documentation/design PRs"),
            new OrgRepo(
                "os-santiago/devopsdays-santiago-webpage",
                "https://github.com/os-santiago/devopsdays-santiago-webpage",
                "Real event issues and PRs"),
            new OrgRepo(
                "os-santiago/open-quest",
                "https://github.com/os-santiago/open-quest",
                "Existing gamified system; possible direct integration"),
            new OrgRepo(
                "os-santiago/github-mission-board",
                "https://github.com/os-santiago/github-mission-board",
                "Missions/bounties; possible integration"));

    OrgRepo homedir = orgRepo("os-santiago/homedir");
    OrgRepo demo = orgRepo("os-santiago/demo-repository");
    OrgRepo webpage = orgRepo("os-santiago/os-santiago.github.io");
    OrgRepo devopsdays = orgRepo("os-santiago/devopsdays-santiago-webpage");
    OrgRepo openQuest = orgRepo("os-santiago/open-quest");
    OrgRepo missionBoard = orgRepo("os-santiago/github-mission-board");

    this.guides =
        List.of(
            guide(
                "quickdraw",
                "Quickdraw",
                "Close an issue or PR within 5 minutes of opening it.",
                "Cierra un issue o PR dentro de los 5 minutos de abrirlo.",
                "achievement",
                "https://github.com/drknzz/GitHub-Achievements#quickdraw",
                1,
                Achievement.VerificationType.MANUAL_ONLY,
                homedir,
                demo),
            guide(
                "pull-shark",
                "Pull Shark",
                "Open a pull request that gets merged.",
                "Abre un pull request que sea mergeado.",
                "achievement",
                "https://github.com/drknzz/GitHub-Achievements#pull-shark",
                1,
                Achievement.VerificationType.MERGED_PRS,
                homedir,
                webpage,
                devopsdays),
            guide(
                "yolo",
                "YOLO",
                "Merge a pull request without a review.",
                "Mergeea un pull request sin revisión.",
                "achievement",
                "https://github.com/drknzz/GitHub-Achievements#yolo",
                1,
                Achievement.VerificationType.MERGED_PRS_NO_REVIEW,
                demo),
            guide(
                "starstruck",
                "Starstruck",
                "Have a repository reach 16 (and more) stars.",
                "Consigue que un repositorio llegue a 16 (o más) estrellas.",
                "achievement",
                "https://github.com/drknzz/GitHub-Achievements#starstruck",
                16,
                Achievement.VerificationType.REPO_STARS,
                homedir,
                openQuest),
            guide(
                "pair-extraordinaire",
                "Pair Extraordinaire",
                "Co-author a merged pull request with another member.",
                "Co-autora un pull request mergeado con otro miembro.",
                "achievement",
                "https://github.com/drknzz/GitHub-Achievements#pair-extraordinaire",
                1,
                Achievement.VerificationType.COAUTHORED_PRS,
                homedir),
            guide(
                "galaxy-brain",
                "Galaxy Brain",
                "Answer and accept discussions on org repositories.",
                "Responde y acepta discusiones en repositorios de la org.",
                "achievement",
                "https://github.com/drknzz/GitHub-Achievements#galaxy-brain",
                1,
                Achievement.VerificationType.MANUAL_ONLY,
                homedir,
                openQuest),
            guide(
                "public-sponsor",
                "Public Sponsor",
                "Sponsor an open-source project publicly.",
                "Patrocina un proyecto open-source públicamente.",
                "highlight",
                "https://github.com/drknzz/GitHub-Achievements#public-sponsor",
                1,
                Achievement.VerificationType.MANUAL_ONLY,
                missionBoard),
            guide(
                "heart-on-your-sleeve",
                "Heart On Your Sleeve",
                "React to issues/PRs/discussions with a heart.",
                "Reacciona con un corazón a issues/PRs/discusiones.",
                "achievement",
                "https://github.com/drknzz/GitHub-Achievements#heart-on-your-sleeve",
                1,
                Achievement.VerificationType.MANUAL_ONLY,
                homedir,
                devopsdays),
            guide(
                "open-sourcerer",
                "Open Sourcerer",
                "Maintain open-source projects with sustained activity.",
                "Mantén proyectos open-source con actividad sostenida.",
                "achievement",
                "https://github.com/drknzz/GitHub-Achievements#open-sourcerer",
                1,
                Achievement.VerificationType.MANUAL_ONLY,
                homedir,
                openQuest));
  }

  /** All achievement guides (achievement + helping org repos), in catalog order. */
  public List<AchievementGuide> guides() {
    return guides;
  }

  /** All org repositories referenced by the catalog. */
  public List<OrgRepo> orgRepos() {
    return orgRepos;
  }

  /** Find an achievement by key. */
  public Achievement find(String key) {
    return guides.stream()
        .map(AchievementGuide::achievement)
        .filter(a -> a.key().equals(key))
        .findFirst()
        .orElse(null);
  }

  private OrgRepo orgRepo(String name) {
    return orgRepos.stream()
        .filter(r -> r.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Org repo not defined: " + name));
  }

  private static AchievementGuide guide(
      String key,
      String title,
      String description,
      String descriptionEs,
      String category,
      String docUrl,
      int threshold,
      Achievement.VerificationType verification,
      OrgRepo... repos) {
    return new AchievementGuide(
        Achievement.of(
            key, title, description, descriptionEs, category, docUrl, threshold, verification),
        List.of(repos));
  }
}
