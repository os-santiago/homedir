# Reputation Hub long-name regression (#1564)

Member names and handles share a shrinking grid track; rank, avatar and score
retain their intrinsic widths. Both the outer row and inner member grid use
`minmax(0, 1fr)`. Linked and fallback names receive one-line ellipsis without
changing fallback text into link styling. Their full Qute-escaped display names
remain in the DOM and in `title` attributes. No translated copy is added.

The regression test in `ReputationHubResourceTest` checks a long linked name with
quotes, ampersands and angle brackets through the running Quarkus application.
The fallback-name test also checks the rendered title and truncation class.
Run from `quarkus-app` with Java 21:

```sh
./mvnw test -Dtest=ReputationHubResourceTest
```

On September 23, 2026 all eight resource tests passed. The exact CSS and template
change was also checked with the isolated Qute/browser validator from
[homedir-ai-sdlc #97](https://github.com/os-santiago/homedir-ai-sdlc/pull/97):
all five row templates, linked/fallback variants, long and short names, at 1024
and 375 pixels. Checks covered visible ellipsis, non-overlap, horizontal overflow,
score position, row height and access to the full name. This component fixture
does not replace the repository's application build and release gates.

This fix is a supervised reference implementation. Remote model pilot attempts
failed layout checks or timed out; successful autonomous delivery is not claimed.
