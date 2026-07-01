# Deepwork Progress — Notification A11y Batch

## Objective
Fix 4 a11y bugs in notification subsystem (issues #1011–#1014) in one atomic PR.

**Branch**: `fix/issue-1011-1014-notification-a11y`
**Base**: `1fa61fb0` (main)

## Phase 1: Notification bell cleanup & badge a11y (#1012)
**Status**: IN PROGRESS
**Files**: `header.html`, `notifications.css`
**Started**: 2026-07-01

## Phase 2: Filter tabs ARIA attributes (#1013)
**Status**: PENDING
**Files**: `center.html`, `notifications-center.js`

## Phase 3: Confirmation dialog ARIA semantics (#1014)
**Status**: PENDING
**Files**: `center.html`

## Phase 4: Toast aria-live regions and focus management (#1011)
**Status**: PENDING
**Files**: `notifications.js`, `notifications.css`

## Oracle Review Notes
- Use `focus-visible` not just `focus` for outline
- Badge hidden must use clip-based hiding (not `display: none`) for `aria-live` to work
- Add `aria-hidden="true"` to Material Symbols icon (defense-in-depth)
