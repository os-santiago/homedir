# Start sections

The home page now differentiates events in two blocks:

## Available events
- Ordered from the closest to the furthest.
- Automatic classification according to the date/time of the event.
- Badge `in progress` when the event has already started but it still does not end.
- Start microcopy: `Starts today`, `Starts in X days` or `Date to be confirmed`.
- Empty state: *"There are no next events for now. Come back 👀"*

## Past events
- Ordered from the most recent to the oldest.
- Badge `finished` for all listed events.
- They are shown with an appearance equivalent to the available events, but in gray scale to indicate that they have already finished.
- Empty state: *"We still don't have previous events."*

## Now Box

A dedicated Now Box block highlights what is happening inside each event that is currently running:

- Displays, per active event, the last finished activity, the one in progress and the next within a configurable window.
- Breaks are labeled explicitly so that the agenda context remains clear even when nothing is on stage.
- Each card links back to the event agenda or talk detail (`/event/{id}`, `/event/{id}/talk/{talkId}` or `#break-{talkId}`) to speed up navigation.
- The list prioritises events with an activity in progress; the rest are ordered by the nearest upcoming item.

Configuration keys in `application.properties` control its behaviour:

- `nowbox.lookback` (default `PT30M`) defines how far into the past the Now Box searches for the “last” activity.
- `nowbox.lookahead` (default `PT60M`) limits how far ahead it looks for the “next” activity.
- `nowbox.refresh-interval` (default `PT30S`) tells the frontend how often to refresh the block.

If no activities fall within the windows, the event is omitted from the Now Box to keep the section focused on immediate context.
