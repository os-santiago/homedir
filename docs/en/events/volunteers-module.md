# Volunteers Module

The Volunteers module extends each event with a controlled volunteer application workflow.

## Product Scope
- Logged-in users can apply as volunteers during a configured application window.
- Admins can review, rate, and update each application status.
- Selected volunteers gain access to a private Volunteer Lounge per event.
- Volunteer progression is reflected in private/public profile panels.

## Status Lifecycle
- `applied`: application created by member.
- `under_review`: admin is actively reviewing the application.
- `selected`: member accepted as volunteer.
- `not_selected`: member was not accepted.
- `withdrawn`: member withdrew their own application.

Applications are immutable by deletion: records are always preserved and status-driven.

## API Surfaces
- Public/member:
  - `GET /api/events/{eventId}/volunteers/submissions/config`
  - `POST /api/events/{eventId}/volunteers/submissions`
  - `PUT /api/events/{eventId}/volunteers/submissions/{id}`
  - `POST /api/events/{eventId}/volunteers/submissions/{id}/withdraw`
  - `GET /api/events/{eventId}/volunteers/submissions/mine`
  - `GET /api/events/{eventId}/volunteers/lounge` (selected/admin)
  - `POST /api/events/{eventId}/volunteers/lounge` (selected/admin)
- Admin:
  - `GET /api/events/{eventId}/volunteers/submissions`
  - `PUT /api/events/{eventId}/volunteers/submissions/{id}/status`
  - `PUT /api/events/{eventId}/volunteers/submissions/{id}/rating`
  - `GET /api/events/{eventId}/volunteers/submissions/stats`
  - `GET|PUT|DELETE /api/events/{eventId}/volunteers/submissions/event-config`

## Guardrails
- Window-based admission control (`opens_at`, `closes_at`, `accepting_submissions`).
- Strict status-transition validation.
- Optimistic concurrency via `expected_updated_at` on moderation actions.
- Lounge posting protections:
  - max body length: 200 chars
  - rate limit: 1 post/minute per user/event
  - event capacity: 500 lounge messages

## Notifications
- On admin status change, applicant receives a user notification in Notifications Center:
  - title: `Volunteer application update`
  - message includes event title and the new status.

## Metrics + Insights
- Funnel metrics emitted:
  - `volunteer_submit`
  - `volunteer_selected`
  - `volunteer_lounge_post`
  - runtime aliases: `volunteer.submission.create`, `volunteer.submission.status.*`, `volunteer.lounge.post`
- Development Insights ledger emits automatic volunteer capability events:
  - `VOLUNTEER_SUBMITTED`
  - `VOLUNTEER_UPDATED`
  - `VOLUNTEER_WITHDRAWN`
  - `VOLUNTEER_STATUS_*`
  - `VOLUNTEER_RATING_UPDATED`
  - `VOLUNTEER_LOUNGE_POSTED`
- Initiative key convention:
  - `event-volunteers-<eventId-slug>`

## Validation Checklist
1. Member can submit and update during window.
2. Admin can move status and set ratings.
3. Status change generates notification for applicant.
4. Selected volunteer can post in lounge; non-selected gets `403`.
5. Funnel rows show volunteer conversion metrics in admin metrics.
6. Admin insights event count increases after volunteer actions.
