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
                List.of(
                    "Fork an os-santiago repository (e.g. homedir or demo-repository)",
                    "Create a new branch for your change",
                    "Make a small improvement (fix a typo, add a test, improve docs)",
                    "Open a pull request with a clear description",
                    "Address any review feedback",
                    "Get the PR merged — achievement unlocked!"),
                List.of(
                    "Haz fork de un repositorio de os-santiago (ej. homedir o demo-repository)",
                    "Crea una nueva rama para tu cambio",
                    "Haz una pequeña mejora (corregir un typo, añadir un test, mejorar docs)",
                    "Abre un pull request con una descripción clara",
                    "Responde a los comentarios de revisión",
                    "Consigue que se mergee el PR — ¡logro desbloqueado!"),
                repo("os-santiago/homedir"),
                repo("os-santiago/demo-repository")),
            guide(
                "quickdraw",
                "Quickdraw",
                "Open and close an issue within 5 minutes. Use the demo-repository sandbox to practice this achievement.",
                "Abre y cierra un issue en menos de 5 minutos. Usa el sandbox demo-repository para practicar este logro.",
                "contribution",
                "https://github.com/drknzz/GitHub-Achievements#quickdraw",
                1,
                List.of(
                    "Go to the demo-repository issues page",
                    "Open a new issue with any title and description",
                    "Immediately close the issue (within 5 minutes)",
                    "Achievement unlocked!"),
                List.of(
                    "Ve a la página de issues del demo-repository",
                    "Abre un nuevo issue con cualquier título y descripción",
                    "Cierra el issue inmediatamente (en menos de 5 minutos)",
                    "¡Logro desbloqueado!"),
                repo("os-santiago/demo-repository")),
            guide(
                "pair-extraordinaire",
                "Pair Extraordinaire",
                "Co-author a merged pull request with another community member. Pair up via the community board.",
                "Co-autoriza un pull request mergeado con otro miembro de la comunidad. Emparejate via el community board.",
                "collaboration",
                "https://github.com/drknzz/GitHub-Achievements#pair-extraordinaire",
                1,
                List.of(
                    "Find a partner on the community board",
                    "Agree on a contribution to work on together",
                    "One person opens the PR, both co-author it",
                    "Add 'Co-authored-by: Name <email>' to the commit",
                    "Get the PR merged — achievement unlocked!"),
                List.of(
                    "Encuentra un compañero en el community board",
                    "Acuerden una contribución para trabajar juntos",
                    "Una persona abre el PR, ambos co-autorizan",
                    "Añade 'Co-authored-by: Nombre <email>' al commit",
                    "Consigue que se mergee el PR — ¡logro desbloqueado!"),
                repo("os-santiago/homedir"),
                repo("os-santiago/devopsdays-santiago-webpage")),
            guide(
                "yolo",
                "YOLO",
                "Merge a pull request without review. Use the demo-repository sandbox (not recommended for production repos).",
                "Mergeea un pull request sin review. Usa el sandbox demo-repository (no recomendado para repos de producción).",
                "contribution",
                "https://github.com/drknzz/GitHub-Achievements#yolo",
                1,
                List.of(
                    "Use the demo-repository sandbox",
                    "Open a pull request",
                    "Merge it without requesting a review",
                    "Achievement unlocked! (Not recommended for production repos)"),
                List.of(
                    "Usa el sandbox demo-repository",
                    "Abre un pull request",
                    "Mézclalo sin solicitar revisión",
                    "¡Logro desbloqueado! (No recomendado para repos de producción)"),
                repo("os-santiago/demo-repository")),
            guide(
                "starstruck",
                "Starstruck",
                "Help an os-santiago repository reach 16 stars. Star and share repos to contribute.",
                "Ayuda a un repositorio de os-santiago a llegar a 16 estrellas. Destaca y comparte repos para contribuir.",
                "community",
                "https://github.com/drknzz/GitHub-Achievements#starstruck",
                16,
                List.of(
                    "Star os-santiago repositories on GitHub",
                    "Share repos with your network",
                    "Encourage others to star them",
                    "When a repo reaches 16 stars — achievement unlocked!"),
                List.of(
                    "Destaca los repositorios de os-santiago en GitHub",
                    "Comparte los repos con tu red",
                    "Anima a otros a destacarlos",
                    "Cuando un repo llegue a 16 estrellas — ¡logro desbloqueado!"),
                repo("os-santiago/homedir"),
                repo("os-santiago/open-quest")),
            guide(
                "galaxy-brain",
                "Galaxy Brain",
                "Answer discussions in os-santiago repos that have Discussions enabled. Multiple accepted answers unlock higher tiers.",
                "Responde discussions en repos de os-santiago que tengan Discussions habilitado. Múltiples respuestas aceptadas desbloquean niveles superiores.",
                "collaboration",
                "https://github.com/drknzz/GitHub-Achievements#galaxy-brain",
                2,
                List.of(
                    "Find repos with Discussions enabled (homedir, github-mission-board)",
                    "Browse open discussions",
                    "Provide helpful answers to questions",
                    "Get at least 2 answers accepted by the question author",
                    "Achievement unlocked!"),
                List.of(
                    "Encuentra repos con Discussions habilitado (homedir, github-mission-board)",
                    "Explora las discussions abiertas",
                    "Proporciona respuestas útiles a las preguntas",
                    "Consigue que al menos 2 respuestas sean aceptadas",
                    "¡Logro desbloqueado!"),
                repo("os-santiago/homedir"),
                repo("os-santiago/github-mission-board")),
            guide(
                "public-sponsor",
                "Public Sponsor",
                "Sponsor an open-source project or contributor publicly via GitHub Sponsors.",
                "Patrocina un proyecto o contribuidor open-source públicamente via GitHub Sponsors.",
                "community",
                "https://github.com/drknzz/GitHub-Achievements#public-sponsor",
                1,
                List.of(
                    "Visit GitHub Sponsors (github.com/sponsors)",
                    "Choose an os-santiago project or contributor to sponsor",
                    "Make a public sponsorship (not anonymous)",
                    "Achievement unlocked!"),
                List.of(
                    "Visita GitHub Sponsors (github.com/sponsors)",
                    "Elige un proyecto o contribuidor de os-santiago para patrocinar",
                    "Haz un patrocinio público (no anónimo)",
                    "¡Logro desbloqueado!"),
                repo("os-santiago/homedir")),
            guide(
                "heart-on-your-sleeve",
                "Heart On Your Sleeve",
                "React to issues and pull requests with heart/emoji reactions across os-santiago repos.",
                "Reacciona a issues y pull requests con emojis de corazón en los repos de os-santiago.",
                "community",
                "https://github.com/drknzz/GitHub-Achievements#heart-on-your-sleeve",
                1,
                List.of(
                    "Browse issues and PRs in os-santiago repos",
                    "React with a heart emoji to issues or PRs you find useful",
                    "Keep reacting to content across the org",
                    "Achievement unlocked!"),
                List.of(
                    "Explora issues y PRs en los repos de os-santiago",
                    "Reacciona con un emoji de corazón a los issues o PRs que encuentres útiles",
                    "Sigue reaccionando al contenido de la org",
                    "¡Logro desbloqueado!"),
                repo("os-santiago/homedir"),
                repo("os-santiago/demo-repository")),
            guide(
                "open-sourcerer",
                "Open Sourcerer",
                "Make substantial open-source contributions across multiple os-santiago repositories.",
                "Haz contribuciones sustanciales de open-source en múltiples repositorios de os-santiago.",
                "contribution",
                "https://github.com/drknzz/GitHub-Achievements#open-sourcerer",
                1,
                List.of(
                    "Contribute to multiple os-santiago repositories",
                    "Open and merge PRs across different repos",
                    "Help with issues, discussions, and reviews",
                    "Build a sustained contribution history",
                    "Achievement unlocked!"),
                List.of(
                    "Contribuye a múltiples repositorios de os-santiago",
                    "Abre y mergea PRs en diferentes repos",
                    "Ayuda con issues, discussions y revisiones",
                    "Construye un historial de contribuciones sostenido",
                    "¡Logro desbloqueado!"),
                repo("os-santiago/homedir"),
                repo("os-santiago/open-quest"),
                repo("os-santiago/github-mission-board")));
  }

  /** Returns all achievement guides in catalog order. */
  public List<AchievementGuide> guides() {
    return guides;
  }

  /** Returns all os-santiago org repos referenced by the catalog. */
  public List<OrgRepo> orgRepos() {
    return orgRepos;
  }

  /** Finds an achievement by key, or {@code null} if not found. */
  public Achievement find(String key) {
    return guides.stream()
        .map(AchievementGuide::achievement)
        .filter(a -> a.key().equals(key))
        .findFirst()
        .orElse(null);
  }

  // -- Helpers --------------------------------------------------------------

  private AchievementGuide guide(
      String key,
      String title,
      String description,
      String descriptionEs,
      String category,
      String docUrl,
      int threshold,
      List<String> steps,
      List<String> stepsEs,
      OrgRepo... repos) {
    return new AchievementGuide(
        new Achievement(
            key, title, description, descriptionEs, category, docUrl, threshold, steps, stepsEs),
        List.of(repos));
  }

  private OrgRepo repo(String name) {
    for (OrgRepo r : orgRepos) {
      if (r.name().equals(name)) {
        return r;
      }
    }
    throw new IllegalArgumentException("Unknown org repo: " + name);
  }
}
