package com.scanales.homedir.trending;

public record TrendingRepo(
    String name,
    String owner,
    String description,
    int stars,
    String language,
    String url,
    String descriptionEs) {

  public TrendingRepo(String name, String owner, String description, int stars, String language, String url) {
    this(name, owner, description, stars, language, url, null);
  }
}
