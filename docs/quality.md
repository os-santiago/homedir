# Code quality policy

This repository runs static analysis on every pull request via the **PR Quality — Static Analysis** workflow. Only the code touched by the PR is scanned to keep feedback fast (under ~4 minutes).

## Severity and gating

| Severity | Meaning | PR effect |
|---|---|---|
| Critical / High | Likely defect, security issue or incorrect behaviour | Blocks the PR until fixed |
| Medium | Potential problem or resource leak | Reported as warning, PR continues |
| Low / Style | Minor or style issues | Informational only |

Only findings introduced in a pull request are gated. Existing findings in `main` are tracked in the baseline and do not block unless touched.

## PR summary

Every pull request shows a quality card with:

- Count of new findings by severity.
- Top three findings, each with a one-line explanation and suggested fix.
- A "View details" link pointing to the full scanner report.

Messages use plain language, for example: "Riesgo de NPE si `foo` viene nulo. Sugerencia: validar antes de acceder".

## Baseline and ownership

The baseline of existing findings lives in `config/quality/baseline.sarif`. Issues already present in `main` are ignored unless the affected lines change. Authors may opt to "take ownership" and fix baseline findings in their PR.

## Exclusions and suppressions

- Generated code and build directories are excluded from analysis.
- Suppress a rule only with justification:

  ```java
  // quality-ignore-next-line: reason
  ```

  or `@SuppressWarnings("rule")` plus a comment. All suppressions must be reviewable and traceable.

## Triage workflow

1. **Refactor** when the finding is valid.
2. **Suppress with justification** if it is a false positive or acceptable risk.
3. **Escalate** to the team when uncertain or the rule needs adjustment.

## Dependency hygiene

Pull requests that modify any `pom.xml` trigger the **PR Quality — Dependencies** job. It provides fast feedback (target ≤ 4 min) on Maven dependency issues.

- **Blocks when gating is enforcing**: duplicate dependencies, open version ranges, broken convergence, or Java version mismatch.
- **Reports** (warn mode): unused or undeclared dependencies detected by `maven-dependency-plugin:analyze`.
- The job summary lists added, updated or removed dependencies plus any violations with hints.

### How to fix

- Align versions in `dependencyManagement` or BOMs.
- Remove duplicates or unused dependencies.
- Move declarations to `dependencyManagement` when versions diverge.

### Run locally

```bash
./dev/deps-check.sh
```

Review the output and resolve warnings before opening a PR.

## Common findings and how to resolve

- **Null pointer risk**: e.g. calling a method on a possibly null object. *Fix:* check for null or use `Optional`.
- **Concurrency issue**: e.g. unsynchronised access to shared mutable state. *Fix:* synchronise or use thread-safe structures.
- **Unclosed resource**: e.g. opening a file/stream without closing. *Fix:* use try-with-resources or close in finally block.
- **Security issue**: e.g. using untrusted input in queries or logs. *Fix:* sanitise input or use prepared statements.
- **Performance smell**: e.g. repeated string concatenation in a loop. *Fix:* use `StringBuilder` or more efficient algorithm.

## Requesting exceptions

If a rule should be excluded repository-wide, open an issue describing the rule and justification. The team will review and update the exclusion list if appropriate.

