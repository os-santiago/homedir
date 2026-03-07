# Metrics System (V1)

Homedir records interaction events and persists them asynchronously to provide insights via the Admin Dashboard.

## Overview
- **Storage**: `data/metrics-v1.json` (Atomic writes, configurable flush interval).
- **Display**: Admin Dashboard (`/private/admin`).
- **Privacy**: No PII in exports; aggregated data only.

## Development Insights Ingest (Optional)
- **Internal endpoint**: `/api/internal/insights/*` (hidden, disabled by default).
- **Guardrails**:
  - `insights.ingest.enabled=false` by default.
  - `X-Insights-Key` required for every request.
- **CI wiring**:
  - `INSIGHTS_INGEST_BASE_URL` (GitHub variable)
  - `INSIGHTS_INGEST_KEY` (GitHub secret)
- **Behavior**: If those values are missing, CI steps skip ingestion without failing builds/releases.
- **Failure signals**: CI/CD also emits failure events when available:
  - `PR_VALIDATION_FAILED`
  - `PRODUCTION_RELEASE_FAILED`
  This allows lead-time dashboards to include non-success outcomes.
- **Quality ratios** (admin insights status):
  - PR validation success rate (`passed / total`)
  - PR validation success rate (last 7d)
  - Production success rate (`production_verified / (production_verified + release_failed)`)
  - Production success rate (last 7d)
- **Short-term delivery trend** (admin insights status):
  - Events in last 7 days
  - Events in previous 7 days
  - Trend delta vs previous 7 days
  - Active initiatives in last 7 days
  - Events in last 30 days
  - Events in previous 30 days
  - Trend delta vs previous 30 days
  - Active initiatives in last 30 days
  - Top event types in last 7 days (top 5)
- **Freshness guardrail** (admin insights status):
  - Minutes since last event
  - Freshness status (`healthy`/`stale`) based on `insights.ledger.stale-minutes` (default 1440)
- **CSV export**: Admin insights can be exported from `/api/private/admin/insights/initiatives/export.csv` (admin only).

## Tracked Events
- **Page Views**: `Page_view: {route}`
- **Event Views**: `Event_view: {eventid}`
- **Talk Views**: `Talk_view: {Talkid}`
- **Talk Registrations**: `Talk_register: {Talkid}` (Idempotent)
- **Stage Visits**: `Stage_visit: {Stageid}: {yyyy-mm-dd}`
- **Speaker Popularity**: `Speaker_popularity: {Speakerid}`
- **CTA Clicks**: Releases, Report Issue, Ko-Fi buttons.

## Dashboard Definitions
The dashboard aggregates these events to show:
- **Records**: Confirmed registrations to talks in range.
- **Visits**: Detail/Listing views for events, home page, and user profiles.
- **Top Lists**: Most visited speakers and scenarios.

## Logic & Trends
- **Conversion Policy**: `Sum(Talk Registrations) / Sum(Talk Views)`.
- **Trends**: Comparison vs previous period. Requires minimum baseline (default 20) to show %.
- **Ranking**: Entities need minimum views (default 20) to appear in top lists.

## Navigation & Filters
- **Filters**: Event, Stage, Speaker, Date Range. Persisted via URL query params.
- **Data Health**: Auto-refresh every 5s. Status indicators for "Outdated" (>2min old) or "No Data".
- **Exports**: CSV export available for visible table rows. Format: `metrics-<table-name>-<range>.csv`.

## Local Validation
1. Run `mvn quarkus:dev`.
2. Browse the site to generate events.
3. Check `quarkus-app/data/metrics-v1.json`.
4. View dashboard at `/private/admin`.
