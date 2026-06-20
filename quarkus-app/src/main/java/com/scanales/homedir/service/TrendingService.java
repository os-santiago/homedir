package com.scanales.homedir.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TrendingService {

    private static final Logger LOG = Logger.getLogger(TrendingService.class);
    private static final String TRENDING_URL = "https://github.com/trending";
    private static final Map<String, String> TRANSLATIONS = Map.ofEntries(
            Map.entry(" a ", " un "), Map.entry(" an ", " un "), Map.entry(" the ", " el "),
            Map.entry(" for ", " para "), Map.entry(" and ", " y "), Map.entry(" with ", " con "),
            Map.entry(" from ", " desde "), Map.entry(" that ", " que "), Map.entry(" this ", " este "),
            Map.entry("library", "biblioteca"), Map.entry("framework", "framework"),
            Map.entry("tool", "herramienta"), Map.entry("platform", "plataforma"),
            Map.entry("server", "servidor"), Map.entry("client", "cliente"),
            Map.entry("database", "base de datos"), Map.entry("build", "construir"),
            Map.entry("run", "ejecutar"), Map.entry("fast", "rápido"),
            Map.entry("simple", "simple"), Map.entry("modern", "moderno"),
            Map.entry("lightweight", "ligero"), Map.entry("management", "gestión"),
            Map.entry("development", "desarrollo"), Map.entry("application", "aplicación"),
            Map.entry("support", "soporte"), Map.entry("based", "basado"),
            Map.entry("using", "usando"), Map.entry("written in", "escrito en"),
            Map.entry("written", "escrito"), Map.entry("open-source", "código abierto"),
            Map.entry("open source", "código abierto"));

    private final HttpClient httpClient;

    public TrendingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<TrendingProject> fetchTrending(String language, int count, String period) {
        String url = TRENDING_URL;
        if (language != null && !language.isBlank()) {
            url += "/" + language;
        }
        if ("weekly".equals(period)) {
            url += "?since=weekly";
        } else if ("monthly".equals(period)) {
            url += "?since=monthly";
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent",
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();
            LOG.infof("Trending HTML fetched: %d bytes, status=%d", html.length(), response.statusCode());
            String[] blocks = html.split("<article");
            LOG.infof("Trending article blocks found: %d", blocks.length - 1);
            return parseTrendingHtml(html, count);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch GitHub trending");
            return List.of();
        }
    }

    private List<TrendingProject> parseTrendingHtml(String html, int maxCount) {
        List<TrendingProject> projects = new ArrayList<>();
        String[] blocks = html.split("<article");
        Pattern slugPattern = Pattern.compile(
                "<h[23][^>]*>.*?<a[^>]*href=\"/([^\"/]+/[^\"/]+?)\"[^>]*>", Pattern.DOTALL);
        Pattern descPattern = Pattern.compile(
                "<p[^>]*class=\"[^\"]*color-fg-muted[^\"]*\"[^>]*>(.*?)</p>",
                Pattern.DOTALL);
        Pattern starPattern = Pattern.compile(
                "octicon-star.*?</svg>\\s*([\\d,]+)", Pattern.DOTALL);

        LOG.infof("Parsing %d article blocks", blocks.length - 1);
        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i];
            Matcher slugMatcher = slugPattern.matcher(block);
            if (!slugMatcher.find()) continue;
            String slug = slugMatcher.group(1);
            if (slug.startsWith("login") || slug.startsWith("sponsors")
                    || slug.startsWith("settings") || slug.startsWith("apps")) {
                continue;
            }

            String[] parts = slug.split("/");
            if (parts.length != 2) continue;
            String autor = parts[0];
            String nombre = parts[1];
            String repoUrl = "https://github.com/" + slug;

            String descripcion = "";
            Matcher descMatcher = descPattern.matcher(block);
            if (descMatcher.find()) {
                descripcion = descMatcher.group(1).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
            }

            String stars = "?";
            Matcher starMatcher = starPattern.matcher(block);
            if (starMatcher.find()) {
                stars = starMatcher.group(1);
            }

            projects.add(new TrendingProject(nombre, autor, repoUrl, descripcion,
                    translateDescription(descripcion), stars));
        }

        // Sort by stars descending (parse "1,234" → 1234)
        projects.sort((a, b) -> {
            int sa = parseStars(a.stars());
            int sb = parseStars(b.stars());
            return Integer.compare(sb, sa); // descending
        });

        // Limit
        if (projects.size() > maxCount) {
            projects = projects.subList(0, maxCount);
        }

        return projects;
    }

    private int parseStars(String s) {
        if (s == null || s.equals("?")) return 0;
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String translateDescription(String desc) {
        if (desc == null || desc.isBlank()) {
            return ""; // ponytail: template handles empty via i18n:trending_no_description
        }
        String result = desc;
        for (var entry : TRANSLATIONS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        if (!result.isEmpty() && Character.isLowerCase(result.charAt(0))
                && Character.isLetter(result.charAt(0))) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }
        return result;
    }

    public record TrendingProject(
            String nombre, String autor, String repoUrl,
            String descripcionEn, String descripcionEs, String stars) {
    }
}
