# Notifications

Iteration 1 introduces the central notification service. Your goal is
Record relevant events for each user without blocking the IU and with
Persistence tolerant of failures.

## Model
`` Java
Class Notification {
  String ID;
  String Userid;
  String Talkid;
  String Eventid;
  Notificationype Type; // Upcoming | Started | Ending_soon | Finished | TEST
  String Title;
  String Message;
  Long Createdat;
  Long readat;
  Long dysmissedat;
  String Dedupekey; // Sha1 (Userid | Talkid | Type | Slot)
  Long Expirsat;
}
``
The deduplication key is generated using a temporary window
(`Notifications.Dupe-Window`, by default 30 min). The same event within
The window produces `dropped_duplicate`.

## LifeCle and Backpressure
1. `enqueue` valida dedupe and limits per user/global.
2. If the persistence tail has capacity and there is disk space,
   program an atomic asynchronous writing per user in
   `Data/Notifications/<Useer> -V1.json` →` accepthed_persisted`.
3. If the tail is full, `accepthed_volatile` or
   `Dropped_capacy` according to` notifications.drop-on-queue-Full`.
4. Periodic tasks eliminate notifications with more than
   `Notifications.retenion-Days` Days.

## Configuration
- `Notifications.
- `Notifications.user-cap` (500)
- `Notifications.global-cap` (100000)
- `Notifications.flush-Interval` (PT10S)
- `Notifications.retenion-Day` (30)
-`Notifications.Max-who-size` (10000)
- `Notifications.Dedupe-Window` (PT30M)
-`Notifications.drop-on-Que-Full` (False)

## Metrics
Minimum counters:
- `Notifications
- `Notifications
- `Notifications.deduped`
- `Notifications.dropped.backpressure`
- `Notifications.volatile.accepted`

Gauges:
- `Notifications.queue.deph`
- `Notifications.users.active`

## Iteration 2 - UI (QUE)
- Toasts container: Fragment Qte `Fragments/Toasts.qute.html` included in the base layout.
- JS Global `Eventflownotifications.accept (Dto)` To glue toasts with stack, self-diamiss and button "Close all".
-Configuration via data-attribute (`data-max-visible`,` data-auto-dismiss-ms`, `data-posseion`).
-Accessibility: `aria-live =" polyite "`, visible focus and support of `prefers -reduced-motion`.
- For development tests: `Window .__ notifydev __ ({...})`.

## next steps
Iteration 3 will add the notification center.

## Iteration 3 - Notification Center

The third iteration exposes an accessible center from a bell in the menu and
A authenticated API to operate notifications.

### Endpoints

The API rests under `/API/Notifications` was removed. Notifications are now
publicly distribute via websockt in `/ws/global-notifications` and the UI
Manage the reading status locally using `localStorage`.

### UX

- Campana with non-read counter (`aria-live =" polyite "`).
- Center with filters *all *, *not read *and *last 24h *.
- Cursor pagination (`createdat`) and action *load more *.
- Unitary and massive actions to mark as read or eliminate.

### Accessibility

- Keyboard navigation and visible focus.
- Contrast AA in the counter and buttons.

## Iteration 4 - Runtime Integration

``
My talks -> evaluator -> notif service -> sse/poll -> ui
``

### Configuration
- `Notifications
- `Notifications
- `Notifications.upcoming.window`
- `Notifications.endingsoon.window`
- `Notifications.sse.enable`
- `Notifications.sse.heartbeat`
- `Notifications.poll.interval`
- `Notifications.poll.limit`
- `Notifications.stream.MaxConneccsperuser`

### Security
- There are no endpoints per user; The global websock is public.
- The non -read counter and the notification center operate only with
  local storage.

### Authentication and session expiration
- Protected HTML pages redirect `/Enter` when the session is not valid.
- The old rest `/API/Notifications` is no longer available.- Session cookies should be issued with `Secure` and` Samesite = None` in production.
 
## Iteration 5 - A11y and Mobile

This iteration improves the accessibility and mobile experience of the module of
Notifications

- Campana with accessible text and accountant `aria-live`.
- Center with semantic landmarks, visible focus and navigable elements by
  keyboard.
- Tap Targets of at least 44 × 44px and AA contrast in buttons and links.
-Support for `Prefers-Reduced-Motion` in CSS and JS, allowing toasts to close
  with <kbd> esc </bd>.
-Layout Mobile-Firs without horizontal scroll, titles with * line-clamp * and
  Containers with `overflow-wrap: Anywhere`.
- Visual performance: high toast reserve, `Aspect-Ratio 'for
  Avatars and Batching of Changes in the DOM to avoid repaints.

## Iteration 6 - Operability and observability

This phase closes the module with metrics and logs structured for operation,
In addition to maintenance tasks and use guide.

### Configuration
- `Notifications.metrics.enable` (True)
- `Notifications.logs.level` (info)
- `Notifications.user-hash.salt` (Changeme)
- `Notifications.retenion-Day` (30)
-`Notifications.Max-File-Size` (3MB)
- `Notifications.Maintenance.Interval` (PT30M)
- `Notifications.backpressure.queue.Max` (10000)
-`Notifications.backpressure.cutoff.evaluator-quee-dept` (8000)
- `Notifications.poll.rarate-limit.window` (PT30s)
- `Notifications.poll.rarate-limit.max` (8)

### Metrics
- `Notifications
- `Notifications.persisted.total`
- `Notifications.deduped.Total`
- `Notifications.dropped.backpressure.total`
- `Notifications.volatile.accepted.total`
- `Notifications.queue.deph`
- `Notifications.users.active`
- `Notifications.Maintenance.purged.total`
- `Notifications.Maintenance.compacted.total`
- `Notifications.Maintenance.Duration.ms`
- API: `Notifications.api.list.requests.total`,` Notifications.api.stream.conneccs.active`,
  `Notifications.api.stream.rejected.max_per_user.total`,` Notifications.api.poll.requests.total`,
  `Notifications.api.erors {code}`

### Logs
- Enqueue: `results`,` Type`, `reason`,` user_hash`.
- SSE events: connection/disconnection with `user_hash` and` reason`.
- Polling served: `items`,` since`, `limit`.

### Maintenance
- Purga notifications with more than `notifications.retenion-days' days.
-Compact Snapshots that exceed `notifications.max-file-size` leaving alone
  Not read and the last ones read.
- Interval controlled by `Notifications.Maintenance.Interval`.

### Documentation
It is added `docs/runbook-notifications.md` with operation procedures,
Backpressure detection and suggested cans.