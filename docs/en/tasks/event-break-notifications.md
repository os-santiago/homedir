# Tasks – Event and break notifications

Status: ✅ Completed. These notes capture the scope that was delivered for reference.

To support the requirement of notifying state changes for events and break slots five minutes before they start or end, we implemented the following tasks:

- [x] Set `notifications.upcoming.window` and `notifications.endingSoon.window` defaults to **PT5M**.
- [x] Create an `EventStateEvaluator` to emit `UPCOMING`, `STARTED`, `ENDING_SOON` and `FINISHED` notifications for the overall event.
- [x] Evaluate break slots (talks marked as `break`) and emit the same state notifications through a dedicated evaluator that reuses the talk logic.
- [x] Surface event and break notifications in the notification center alongside talk alerts.
- [x] Update documentation to describe event and break coverage and the new five‑minute windows.
- [x] Add integration tests that verify event and break notifications are enqueued at the expected times.
