# Tasks – Event and break notifications

To support the requirement of notifying state changes for events and break slots five minutes before they start or end, implement the following tasks:

- [ ] Set `notifications.upcoming.window` and `notifications.endingSoon.window` defaults to **PT5M**.
- [ ] Create an `EventStateEvaluator` to emit `UPCOMING`, `STARTED`, `ENDING_SOON` and `FINISHED` notifications for the overall event.
- [ ] Evaluate break slots (talks marked as `break`) and emit the same state notifications, reusing `TalkStateEvaluator` or introducing a dedicated evaluator.
- [ ] Surface event and break notifications in the notification center alongside talk alerts.
- [ ] Update documentation to describe event and break coverage and the new five‑minute windows.
- [ ] Add integration tests that verify event and break notifications are enqueued at the expected times.
