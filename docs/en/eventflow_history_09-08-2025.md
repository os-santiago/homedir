# Eventflow - Development History  
*From zero to production with quarkus + oidc + qute

---

## 1) History from the point of view of ** User Requirements **

### Vision
- ** Eventflow ** It is born as a free app to*discover events and navigate its content*(scenarios, talks, schedules) in a simple, modern and very visual way **.
- We prioritize: ** Information clarity **, ** Intuitive navigation **, ** Low impact ** (Cost/footprint) and ** Speed ​​delivery **.

### epic and functionalities
- ** Discovery & Navigation **
  - Home with *event cards *, ordered by temporal proximity ("missing for days").
  - Event detail with ** Agenda per day **, scenarios and walked talks.
  - Bidirectional flow: * event * ⇄ * scenario * ⇄ * talk * (cross bonds).
  - Button "** How to get **": show image of the stage map.
- ** Profile and customization **
  - "** My talks **": Personal record of favorite talks.
  - states for time: *on time * / * / * / *in progress * / *finished *.
  - UX improvements: success/error feedback, “Go to my talks” button.
- **Administration**
  - Crud of ** Events **, ** Scenarios ** and ** Talks ** (Access restricted by admin-list).
  - Auto-ID by Timestamp (not asking ID).
  - ** Event date ** Administrable; Home/End of the event calculated from the agenda.
  - ** Mapurl ** Administrable (Link “View Map” in Detail).
  - Import/export data: went to ** backup/restore ** (zip) when we add persistence.
- ** Speakers (speakers) **
  - Reusable model: *Speaker ↔ Talk *; A talk belongs to a speaker; A talk can be included in multiple events.
  - Speaker detail with biography and talks.
- ** UX/UI **
  - Elimination of redundant entries from the menu (eg "events" if I have shown them).
  - Header with ** Logos ** (Eventflow + "Powered by Opensourcesantiago").
  - ** Project Header ** (State Dashboard): version, releases, ISSUES and KO-FI.
  - Home with * Timeline * and layout consistent with identity (water/wind, "code snippets", console/chat).

---

## 2) History from the point of view of ** Technical evolution **

### Authentication & Authorization
- ** Oidc with Google **  
  - Initial incidents: `invalid_client`,` ~ redirect_uri_mismatch`, jwks and scopes.
  - Quarkus oidc settings: `redirect-path`,` scopes = openid profile email`, callback correction and reliable obtaining*email*and*claims*(from ** id token ** or*userinfo*).
  - Logout: correction of flows and tests (303 expected, head `location`).

### Rendered & Templates (QUE)
- Typical errors resolved:
  - Badly closed tags (`{#main}` / `{ / main}` `{#raw}` / `{ / raw}`).
  - Double `{#insert}` and *partials *with `include` + *slots *.
  - Logic leaks to HTML (appeared `{: ELSE IF ...}` in view): Conditional cleaning.
- Refactor: * partials * reusable and consistent styles.

### Data & Persistence
- ** PHASE 1 (IN-MEMORY) **  
  - `EntService` with` concurrenthashmap`.  
  - Problem: In cluster/scale or reinforcements, the data disappeared → * export emptiness * and inconsistent views.
- ** Phase 2 (light persistence) **
  - ** PVC of Kubernetes ** as app directory of the app.
  - Asynchronous layer of ** Persistence tail **: Each change in event/speaker/talk is serialized to Json (Json-B/Jackson supported by Quarkus) and is written to disk.
  - ** Load to the start **: Bootstrap of data from the PVC and*Logs*explicit of the process.
  - ** Backup/Restore Manual **: Zip of the last data and safe restoration photo (atomic replacement; validations and logs).
  - Dashboard Admin: Simple metric of available space and basic alerts.
- ** Git Sync **  
  -Initial attempt (JGIT) for “events such as code” → blocked by ** native errors ** (Mandrel/Graralvm) and `` `` `` `` `` `` fragile `` `` `` configuration.  
  - Decision: ** Remove git ** from the Runtime (v2.0.0) and focus on ** local persistence + backup **.

### JSON and LIBRERIAS- Use of ** JSON-B (Yasson) ** O ** Jackson ** (both supported in Quarkus + Java 21).  
- Good practices:
  - Dtos like * Pojos * o `Record`.
  - `Java.time` (` `localdatetime`,` localtime`) with consistent formats.
  -`Fail-on-undaknown` as case for compatibility; Include only non-null if applied.
  - Validation with Hibernate Validator (`@notnull`,`@email`, etc.).
  -Tests with `quarkus-junit5` +` rest-over` for serialization and endpoints.

### Observability & Quality
- Reduction of * spam * of irrelevant logs.
- ** Logs of critical steps ** with class, method and objective (e.g., “persist.save (eventid =…) ok”).
- Page admin of ** State ** (initial load, persistence errors, metrics).
-Review of *Dead Code *, duplications (`filldefaults`), safe streams/io use (try-with-resource).

### Ci/CD and GITHUB FLOW
- Scripts `gh` for *issues *, *branches *and *PRS *from Powershell.
- Reversions and *tags *: return to `v1.1.0` as a stable base and mark` v2.0.0` without git.
- Network problems in `MVN test` → mitigations (local rests, reintents, flags).

---

## 3) History from the point of view of ** Development assisted by Ia (codex/chatgpt) **

### what ** worked well **
- ** Short and focused iterations **: Prompts by*ISSUE*With*Objective/Scope/Files/Acceptance/Testing Criteria*.  
- ** Prompts “PRD style” ** (user requirements): We avoid imposing solutions; We describe observable results.
- ** Log diagnosis **: Include*Stack traces*, exact urls, payloads and*Headers*helped close rapid incidents.
- ** Prompt templates for UI **: specify*paths*,*classes/ids css*, "do not do",*responsive*and*accessibility*.
- ** Oidc Disambiguation **: Listar Scopes, Callback, `Redirect-Path`, how to get Claims out of ** ID token ** and*userinfo*.

### what ** did not work well **
- ** Prompts “Megapaque” **: Too many changes in one → regressions and*loops*of incidents.  
- ** Technical assumptions ** In user requirements: QTE was broken by small details (Tags, Inserts).  
-*  
- ** Local environment **:*redirect_uri*,*jwks*,*/q/oauth2/callback*,*secure cookies*… they varied between DEV/prod.

### Learnings (how to ask for better)
- ** Recommended prompt format **
  1. ** Objective ** (What changes for the user).
  2. ** SCOPE ** (What if / what does not touch).
  3. ** Files and routes ** Exact edit/create.
  4. ** Visual requirements ** (Layout, components, styles, responsive).
  5. ** Acceptance criteria ** with concrete examples.
  6. ** Manual tests ** and*rules of non -regression*.
  7. ** Do not do ** (explicit).
- ** Separate features incidents **: first stabilize (errors 500, templates), then UI/UX.
- ** ACTIONABLE LOGS **: A line by critical step, with*context*(class/method/ids).
- ** Rapid reversion **: If a technical thread (eg native JGIT) is soaked,*withdraw and continue*; Document as debt.
- ** Realistic data **: For views and validations, provide examples (event with 2 days, 3 scenarios, 5 talks).

### Playbook for future iterations
- ** Checklists ** Initials (Oidc, Qte, Persistence, Routes).
- ** Short branches ** + PRS guys with*Review Checklist*(Accessibility, Responsive, Logs).
- ** Feature Flags ** for navigation changes and layouts.
- ** Backups and migrations **: Data version aligned with APP version (compatibility).
- ** UX traceability **: Each iteration starts from a*User Story*and ends with*Checked criteria*in Prod.

---

## Closing (current state)
- Stable version without git in runtime, with ** local persistence ** (PVC) and ** backup/restore ** manual.
- Lighter UX: Home as Event Hub, ** Project Header ** Informative, Simple Navigation Event ⇄ Scenario ⇄ Talk, “My talks” with feedback and filters.
- Solid base to follow: ** reusable speakers **, ** AGENDA ** Precise based on the date of the event, and ** Observability ** Minimum List.** Next focus **: polish “my events/my talks”, complete speaker administration↔ reusable, reinforce E2E and accessibility tests, and continue with incremental visual improvements without breaking the simple flow of the site.