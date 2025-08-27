# Notifications

This module delivers in-app notifications for changes in the talks tracked in **My Talks**. It is
UX-first and never blocks user interaction.

## State rules and thresholds
- `UPCOMING`: begins in ≤ `notifications.upcoming.minutes` (default 15).
- `STARTED`: current time between start and end.
- `ENDING_SOON`: ≤ `notifications.ending.minutes` to finish (default 10).
- `FINISHED`: end time passed.

## Persistence and cleanup
Notifications are stored per user in memory and asynchronously persisted to `data/notifications.json`.
A maximum of 500 notifications per user is kept and entries older than 30 days are removed
periodically.

## Backpressure behavior
The service evaluates memory and disk capacity before persisting. If resources are low the
notification is kept in memory only and marked as dropped due to backpressure.

## Accessibility and mobile
Toasts and the future notification center follow mobile-first guidelines: large tap targets,
visible focus, AA contrast and respect for `prefers-reduced-motion`.
