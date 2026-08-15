package com.scanales.homedir.trending;

public record TrendingRepo(
    String name,
    String owner,
    String description,
    int stars,
    int starsToday,
    int forks,
    int contributors,
    String language,
    String url,
    String descriptionEs) {

  public TrendingRepo(
      String name, String owner, String description, int stars, String language, String url) {
    this(name, owner, description, stars, 0, 0, 0, language, url, null);
  }

  public TrendingRepo(
      String name,
      String owner,
      String description,
      int stars,
      String language,
      String url,
      String descriptionEs) {
    this(name, owner, description, stars, 0, 0, 0, language, url, descriptionEs);
  }

  public TrendingRepo(
      String name,
      String owner,
      String description,
      int stars,
      int starsToday,
      int forks,
      int contributors,
      String language,
      String url,
      String descriptionEs) {
    this.name = name;
    this.owner = owner;
    this.description = description;
    this.stars = stars;
    this.starsToday = starsToday;
    this.forks = forks;
    this.contributors = contributors;
    this.language = language;
    this.url = url;
    this.descriptionEs = descriptionEs;
  }
}
