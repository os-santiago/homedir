# Prompt for codex - Iteration 1 · Dashboard of metrics (MVP)

## Objective (Business Vision)
As administrator/or I want a simple and fast metric dashboard that shows me, at a glance, the key activity of the site to make decisions without navigating multiple screens. You must load quickly, be clear and not expose PII.

## Scope - Implementation (application code)

1) Display/Admin route → “metric”
   - Create/update the unique view of Dashboard.
   - Show “Last update: X min” based on the timestamp of data shown (not the system).
   - Skeleton Loaders short while the data is resolved.

2) Global range selector (applies to the entire Dashboard)
   - Options: Today / last 7 days / last 30 days / the entire event.
   - Use the same time zone of the event.
   - When changing the range, refresh cards and tables without navigation and without blocking the UI.

3) Summary cards (upper row)
   - "Records to my talks (range)" - Total within the range.
   - "Visits to events (range)" - Total within the range.
   - "Visits at Start (Rank)" - Total within the range.
   - "User profile visits (range)" - Total within the range.
   - "Ctas (rank)" - Three visible counters: releases | Report ISSUE | Ko-fi ☕.
   - Under each card, short secondary text explaining what is counted (without technicalities).
   - Empty states: show “0” and the explanatory text (not hide the card).

4) Essential tables (Top 10, without pagination)
   - “Talks with more records (range)” - Columns: talk · event · records.
   - “Most visited events (range)” - Columns: Event · Visits.
   - “Speakers more visited (range)” - Columns: speaker · Profile visits.
   - “Most visited scenarios (rank)” - Columns: scenario · event · Visits.
   - Descending order for the main metric; Maximum 10 rows.
   - Placeholder when there is no data: "Without sufficient data in this range."

5) Data adapters (reading only; without PII)
   - Connect with existing metric sources and apply the selected range.
   - Mapear IDS to legible names (talk, event, speaker, stage) using existing services/dominoes.
   - Ensure correct aggregations by range for each card/table.
   - Performance: Avoid n+1; Make a single reading by refresh (reuse Snapshot/cache if it exists).
   - Do not enter new personal identifiers or sensitive data.

6) Presentation states and errors
   - Show Placeholders and clear messages in the absence of data.
   - Friendly management of reading errors (non -technical message; not blocking all the view).

7) Accessibility and Responsive (Minimum)
   - Accessible labels/ARIA on cards and tables.
   - Logical tabing order.
   - Correct behavior in medium screens (laptop).
   -Add Data-Testids for Qa (Ex.: `Data-Testid =" Metrics-Card-Registrations "`).

8) Performance (Product Budget)
   - Initial load perceived <300 ms with typical data.
   - Fluid range transitions without jank.

## Scope - Documentation (docs to register)

D1) Functional definitions (metric dictionary)
- "Records to my talks": Total records confirmed to talks within the range.
- “Visits to events”: Sum of views of detail/listing pages of each event within the range.
- “Visits at Start”: Views of the home page within the range.
- "User profile visits": views of the profile section (aggregate, no PII).
- "Speakers most visited": views of the speaker profile within the range.
- "Most visited scenarios": views of the stage (and its event) within the range.
- "Ctas": click on "Releases", "Report Issue", "Ko-Fi".

D2) Dashboard use guide (short readme in `docs/`)
- What shows each card/table.
- How ranges and "last update" work.
- Common states ("without sufficient data ...").

D3) Copys/UX (text glossary)- Titles/card labels and tables.
- State messages (Loaders, without data, non -technical error).

D4) Performance and privacy criteria
- Load budget (<300 ms).
- Confirmation of "Sin Pii".
- Good aggregation/reading practices.

## Acceptance criteria (DOD)

- Code -
- CA1: Cards show correct totals according to the selected range.
- CA2: Tables Top 10 ordered by its metric; Maximum 10 rows; Placeholder When applying.
- CA3: Change rank cool cards and tables consistently and fluidly.
- CA4: “Last update” is calculated based on the data shown and is readable (“MIN MIN”).
- CA5: Initial load <300 ms with typical data; transitions without blockages.
- Ca6: Without Pii in UI; Minimum accessibility (ARIA, Tab-Aorder, contrast).

- Docs -
- CA7: There is an updated metric dictionary.
- CA8: There is a brief guide for the use of the dashboard in `docs/`.
- CA9: Copys/UX documented to maintain consistency.

## Functional tests (user/operation)
- Change between today / 7 days / 30 days / the entire event → figures change consistently.
- Rank without activity in any category → See “without sufficient data” only in that table.
- Verify that sums and total are consistent by range.
- Validate basic accessibility (Tab-Aorder, Labels) and Responsive in Laptop.
- Verify that "updated x min" changes after a new refresh.

## Out of reach (iteration 1)
- "See" actions to detail, search and export screens (iteration 2).
- Trends (%δ), comparative and peaks (iteration 3).
- Additional Scenario/Scenario/Steaker (Iteration 4).
- Insights of extended ctas (historical with means/deviations) (iteration 5).
- Health status of the data module (iteration 6).