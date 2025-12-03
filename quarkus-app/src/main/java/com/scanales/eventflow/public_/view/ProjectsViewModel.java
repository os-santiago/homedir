package com.scanales.eventflow.public_.view;

import java.util.List;

public class ProjectsViewModel {

  public String orgName;
  public String orgDescription;

  public long totalRepositories;
  public long activeRepositories;
  public long totalStars;
  public long totalContributors;

  public List<ProjectSummary> featuredProjects;

  public static ProjectsViewModel mock() {
    ProjectsViewModel vm = new ProjectsViewModel();
    vm.orgName = "OSS Santiago";
    vm.orgDescription = "Organización OSS para herramientas de comunidad, eventos y plataformas.";

    vm.totalRepositories = 12;
    vm.activeRepositories = 7;
    vm.totalStars = 420;
    vm.totalContributors = 25;

    vm.featuredProjects =
        List.of(
            new ProjectSummary(
                "homedir",
                "Plataforma modular para comunidades y eventos, construida sobre Quarkus.",
                "Java",
                120,
                24,
                10,
                "Hace 2 horas",
                "https://github.com/os-santiago/homedir",
                98,
                8),
            new ProjectSummary(
                "eventflow",
                "Módulo de gestión de eventos y agenda para la comunidad.",
                "Java",
                80,
                12,
                5,
                "Ayer",
                "https://github.com/os-santiago/eventflow",
                88,
                6),
            new ProjectSummary(
                "oss-site",
                "Sitio web público de OSS Santiago con contenidos y enlaces.",
                "TypeScript",
                60,
                10,
                3,
                "Hace 3 días",
                "https://github.com/os-santiago/oss-site",
                70,
                4),
            new ProjectSummary(
                "community-tools",
                "Utilidades y scripts para automatizar flujos de la comunidad.",
                "Python",
                40,
                6,
                2,
                "Hace 1 semana",
                "https://github.com/os-santiago/community-tools",
                64,
                3),
            new ProjectSummary(
                "oss-playground",
                "Repositorio de experimentos, demos y pruebas de concepto.",
                "Varios",
                30,
                5,
                1,
                "Hace 2 semanas",
                "https://github.com/os-santiago/oss-playground",
                50,
                2));

    return vm;
  }

  public static class ProjectSummary {
    public String name;
    public String description;
    public String primaryLanguage;
    public int stars;
    public int forks;
    public int openIssues;
    public String lastActivity;
    public String url;
    public int activityScore;
    public int activeContributors;

    public ProjectSummary(
        String name,
        String description,
        String primaryLanguage,
        int stars,
        int forks,
        int openIssues,
        String lastActivity,
        String url,
        int activityScore,
        int activeContributors) {
      this.name = name;
      this.description = description;
      this.primaryLanguage = primaryLanguage;
      this.stars = stars;
      this.forks = forks;
      this.openIssues = openIssues;
      this.lastActivity = lastActivity;
      this.url = url;
      this.activityScore = activityScore;
      this.activeContributors = activeContributors;
    }
  }
}
