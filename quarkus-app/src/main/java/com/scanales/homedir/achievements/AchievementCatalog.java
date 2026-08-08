package com.scanales.homedir.achievements;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Static catalog of GitHub achievements the os-santiago community can work toward, together with
 * the org repositories that help earn each one.
 *
 * <p>The catalog mirrors the achievements listed in issue #1043 and the drknzz/GitHub-Achievements
 * reference. Thresholds are based on the minimum tier for each achievement. Each achievement
 * includes bilingual step-by-step guides for interactive walkthroughs.
 */
@ApplicationScoped
public class AchievementCatalog {

  /** A GitHub achievement/highlight that HomeDir helps community members unlock. */
  public record Achievement(
      String key,
      String title,
      String description,
      String descriptionEs,
      String category,
      String docUrl,
      int threshold,
      int xpReward,
      List<String> steps,
      List<String> stepsEs) {}

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
                "Webpage PRs (Pull Shark, Pair Extraordinaire)"),
            new OrgRepo(
                "os-santiago/open-quest",
                "https://github.com/os-santiago/open-quest",
                "Quest-based PRs and issues"),
            new OrgRepo(
                "os-santiago/github-mission-board",
                "https://github.com/os-santiago/github-mission-board",
                "Mission board contributions"));

    this.guides =
        List.of(
            guide(
                "pull-shark",
                "Pull Shark",
                "Open a pull request in any os-santiago repo and get it merged. The first merged PR unlocks this achievement.",
                "Abre un pull request en cualquier repo de os-santiago y consigue que se mergee. El primer PR mergeado desbloquea este logro.",
                "contribution",
                "https://github.com/drknzz/GitHub-Achievements#pull-shark",
                1,
                50,
                List.of(
                    "Fork an os-santiago repository (e.g. homedir or demo-repository)",
                    "Create a new branch for your change",
                    "Make a small improvement (fix a typo, add a test, improve docs)",
                    "Open a pull request with a clear description",
                    "Wait for review and address feedback",
                    "Once merged, the achievement appears on your GitHub profile"),
                List.of(
                    "Haz fork de un repositorio de os-santiago (ej. homedir o demo-repository)",
                    "Crea una nueva rama para tu cambio",
                    "Haz una pequeña mejora (corregir un typo, añadir un test, mejorar docs)",
                    "Abre un pull request con una descripción clara",
                    "Espera la revisión y addressing feedback",
                    "Una vez mergeado, el logro aparece en tu perfil de GitHub")),
            guide(
                "yolo",
                "YOLO",
                "Merge a pull request without a review. Use the demo-repository sandbox for this.",
                "Mergea un pull request sin revisión. Usa el sandbox demo-repository para esto.",
                "contribution",
                "https://github.com/drknzz/GitHub-Achievements#yolo",
                1,
                30,
                List.of(
                    "Use os-santiago/demo-repository as your sandbox",
                    "Create a branch and make a trivial change",
                    "Open a PR and merge it yourself (if you have write access)",
                    "Or ask a maintainer to merge without review",
                    "The achievement appears after the first self-merged PR"),
                List.of(
                    "Usa os-santiago/demo-repository como sandbox",
                    "Crea una rama y haz un cambio trivial",
                    "Abre un PR y mergealo tú mismo (si tienes acceso de escritura)",
                    "O pide a un maintainer que mergee sin revisión",
                    "El logro aparece después del primer PR auto-mergeado")),
            guide(
                "quickdraw",
                "Quickdraw",
                "Close an issue or PR within 5 minutes of opening it. The demo-repository is ideal for this.",
                "Cierra un issue o PR dentro de 5 minutos de abrirlo. demo-repository es ideal para esto.",
                "contribution",
                "https://github.com/drknzz/GitHub-Achievements#quickdraw",
                1,
                20,
                List.of(
                    "Open an issue in os-santiago/demo-repository",
                    "Immediately close it (within 5 minutes)",
                    "The achievement appears on your profile",
                    "Tip: label it as 'self-closed' for clarity"),
                List.of(
                    "Abre un issue en os-santiago/demo-repository",
                    "Ciérralo inmediatamente (dentro de 5 minutos)",
                    "El logro aparece en tu perfil",
                    "Tip: etiquétalo como 'self-closed' para claridad")),
            guide(
                "pair-extraordinaire",
                "Pair Extraordinaire",
                "Co-author a commit with another contributor. Use co-authored-by trailers in your commits.",
                "Co-autoriza un commit con otro contribuidor. Usa el trailer co-authored-by en tus commits.",
                "collaboration",
                "https://github.com/drknzz/GitHub-Achievements#pair-extraordinaire",
                1,
                50,
                List.of(
                    "Find another community member to pair with",
                    "Make a commit with a Co-authored-by trailer",
                    "Example: Co-authored-by: Name <email@example.com>",
                    "Push the commit and open a PR in any os-santiago repo",
                    "Once merged, the achievement appears"),
                List.of(
                    "Encuentra otro miembro de la comunidad para emparejar",
                    "Haz un commit con el trailer Co-authored-by",
                    "Ejemplo: Co-authored-by: Name <email@example.com>",
                    "Push el commit y abre un PR en cualquier repo de os-santiago",
                    "Una vez mergeado, el logro aparece")),
            guide(
                "starstruck",
                "Starstruck",
                "Star 16+ repositories. The os-santiago org has multiple repos to star.",
                "Marca como favorito (star) 16+ repositorios. La org os-santiago tiene múltiples repos para marcar.",
                "social",
                "https://github.com/drknzz/GitHub-Achievements#starstruck",
                16,
                60,
                List.of(
                    "Visit each os-santiago repository",
                    "Click the Star button on each one",
                    "Star other repositories you find interesting",
                    "Once you've starred 16 repos, the achievement appears",
                    "Track your progress on your GitHub profile page"),
                List.of(
                    "Visita cada repositorio de os-santiago",
                    "Haz clic en el botón Star en cada uno",
                    "Marca otros repositorios que te parezcan interesantes",
                    "Una vez que hayas marcado 16 repos, el logro aparece",
                    "Sigue tu progreso en tu página de perfil de GitHub")),
            guide(
                "galaxy-brain",
                "Galaxy Brain",
                "Answer a question in GitHub Discussions and have it accepted. Repos with Discussions enabled qualify.",
                "Responde una pregunta en GitHub Discussions y que sea aceptada. Los repos con Discussions habilitado califican.",
                "collaboration",
                "https://github.com/drknzz/GitHub-Achievements#galaxy-brain",
                1,
                50,
                List.of(
                    "Find a repo with GitHub Discussions enabled",
                    "Look for unanswered questions in the Discussions tab",
                    "Provide a helpful, detailed answer",
                    "Wait for the question author to accept your answer",
                    "The achievement appears after your first accepted answer"),
                List.of(
                    "Encuentra un repo con GitHub Discussions habilitado",
                    "Busca preguntas sin responder en la pestaña Discussions",
                    "Proporciona una respuesta útil y detallada",
                    "Espera a que el autor de la pregunta acepte tu respuesta",
                    "El logro aparece después de tu primera respuesta aceptada")),
            guide(
                "public-sponsor",
                "Public Sponsor",
                "Sponsor an open-source project publicly on GitHub.",
                "Patrocina un proyecto open-source públicamente en GitHub.",
                "social",
                "https://github.com/drknzz/GitHub-Achievements#public-sponsor",
                1,
                40,
                List.of(
                    "Visit the Sponsors page of any open-source maintainer",
                    "Choose a sponsorship tier",
                    "Make your sponsorship public (not anonymous)",
                    "The achievement appears on your profile"),
                List.of(
                    "Visita la página de Sponsors de cualquier maintainer open-source",
                    "Elige un tier de patrocinio",
                    "Haz tu patrocinio público (no anónimo)",
                    "El logro aparece en tu perfil")),
            guide(
                "heart-on-your-sleeve",
                "Heart On Your Sleeve",
                "Sponsor 5+ open-source projects publicly on GitHub.",
                "Patrocina 5+ proyectos open-source públicamente en GitHub.",
                "social",
                "https://github.com/drknzz/GitHub-Achievements#heart-on-your-sleeve",
                5,
                30,
                List.of(
                    "Sponsor multiple open-source maintainers",
                    "Make all sponsorships public",
                    "Once you've sponsored 5+ projects, the achievement upgrades",
                    "Track your sponsorships on your GitHub profile"),
                List.of(
                    "Patrocina múltiples maintainers open-source",
                    "Haz todos los patrocinios públicos",
                    "Una vez que hayas patrocinado 5+ proyectos, el logro se actualiza",
                    "Sigue tus patrocinios en tu perfil de GitHub")),
            guide(
                "open-sourcerer",
                "Open Sourcerer",
                "Sponsor 10+ open-source projects publicly on GitHub.",
                "Patrocina 10+ proyectos open-source públicamente en GitHub.",
                "social",
                "https://github.com/drknzz/GitHub-Achievements#open-sourcerer",
                10,
                80,
                List.of(
                    "Continue sponsoring open-source maintainers",
                    "Keep all sponsorships public",
                    "Once you've sponsored 10+ projects, the achievement upgrades",
                    "This is the highest sponsorship tier achievement"),
                List.of(
                    "Continúa patrocinando maintainers open-source",
                    "Mantén todos los patrocinios públicos",
                    "Una vez que hayas patrocinado 10+ proyectos, el logro se actualiza",
                    "Este es el logro de patrocinio de mayor nivel")));
  }

  private AchievementGuide guide(
      String key,
      String title,
      String description,
      String descriptionEs,
      String category,
      String docUrl,
      int threshold,
      int xpReward,
      List<String> steps,
      List<String> stepsEs) {
    Achievement achievement =
        new Achievement(
            key,
            title,
            description,
            descriptionEs,
            category,
            docUrl,
            threshold,
            xpReward,
            steps,
            stepsEs);
    List<OrgRepo> relevantRepos = reposForCategory(category);
    return new AchievementGuide(achievement, relevantRepos);
  }

  private List<OrgRepo> reposForCategory(String category) {
    return switch (category) {
      case "contribution" -> List.of(orgRepos.get(0), orgRepos.get(1), orgRepos.get(3));
      case "collaboration" -> List.of(orgRepos.get(0), orgRepos.get(1), orgRepos.get(4));
      case "social" -> List.of(orgRepos.get(0), orgRepos.get(2), orgRepos.get(5));
      default -> orgRepos;
    };
  }

  public List<AchievementGuide> guides() {
    return guides;
  }

  public List<OrgRepo> orgRepos() {
    return orgRepos;
  }

  public AchievementGuide guideForKey(String key) {
    return guides.stream().filter(g -> g.achievement().key().equals(key)).findFirst().orElse(null);
  }
}
