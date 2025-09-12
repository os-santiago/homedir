# Use metrics (V1)

The system records interaction events and persists them asynchronously in `data/metrics-v1.json`.

## Events

- `Page_view: {route}`
- `Event_view: {eventid}`
- `Talk_view: {Talkid}` (briefly deduced per session)
- `Talk_register: {Talkid}` (Idempotent per user+talk)
-`Stage_visit: {Stageid}: {yyy-mm-dd}` (event hourly zone)
- `Speaker_popularity: {Speakerid}` (record derivative)

## Persistence

The counters are kept in memory and are kept periodically in `data/metals-v1.json` using atomic writings. The interval can be configured with `metrics.flush-interval` (default 10s).

## Local validation

1. Start the application:
   `` Bash
   mvn -f quarkus -app/pom.xml quarkus: dev
   ``
2. Navigate the site and record a talk.
3. See file `quarkus -app/data/metrics-v1.json` to observe the counters.

## Reading in admin → metrics

The administration view directly reads `data/metrics-v1.json` and shows:

- Summary cards with events of events, talks views, records and visits to scenarios.
- Global conversion and expected assistants (approx. Sum of records).
- Top 10 of talks, speakers and scenarios with better conversion.
- Export to CSV of visible content.

The keys are mapped to existing entities using memory services:

- `Talk_*` → Title of the talk (`eventservice.findtalk`).
- `Speaker_popularity:*` → Speaker's name (`Speakerservice.getspeaker`).
- `Stage_visit:*` → stage name (`eventservice.findscenario`).

### Conversion

- ** Talks: ** `Talk_register: {Talkid} / talk_view: {talkid}`. If the views are 0 it is shown " -".
- ** Event: ** The policy ** A ** (sum of records / sum of their talks) is used. The alternative ** B ** (simple average per talk) was considered but is not currently used.
- ** Scenarios and speakers: ** Aggregates of the associated talks.

To avoid biases, a minimum view of views is applied (`Metrics.min-vie-threshold`, Default 20) so that a talk, stage or speaker appears in the rankings.

### Trends

The calculation of variations uses the parameters:

- `Metrics.trend.min-baseline` (Default 20): Minimum of the base to show percentages.
- `Metrics.trend.decimals` (Default 1): Decimals when | δ | <10%.


If there is not enough data, the panel shows an informative message instead of empty tables.