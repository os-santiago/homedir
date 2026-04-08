---
description: Build, Test, Deploy to Production, and Verify
---

This workflow automates the cycle of verifying code locally, deploying it to production, and verifying the live site.

## 1. Local Verification
1.  **Build & Test**: Run the full test suite to ensure stability.
    - Command: `./mvnw clean test`
    - *Stop if this fails.*

## 2. Deployment (Production Fix Cycle)
1.  **Create Branch**: Create a descriptive feature branch (if not already on one).
2.  **Commit & Push**: Commit all pending changes and push to origin.
3.  **Create PR**: Open a Pull Request targeting `main`.
    - Command: `gh pr create --title "feat/fix: [description]" --body "[details]"`
4.  **Auto-Merge**: Enable auto-merge to deploy as soon as checks pass.
    - Command: `gh pr merge --merge --auto`
5.  **Monitor Pipeline**: Watch the release pipeline until deployment is complete.
    - Command: `gh run list --limit 1` (to get ID)
    - Command: `gh run watch [RUN_ID]`

## 3. Production Verification
1.  **Run Verification Workflow**: Execute the site revision workflow.
    - Command: Use the `production_verification` workflow to check Home, Community, Projects, and Events pages.
