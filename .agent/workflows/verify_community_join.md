---
description: Verify the Community page functionality and the "Join Community" flow in the browser.
---

This workflow validates the user experience for joining the community, including authentication states and feedback.

1. **Navigate to Community Page**:
   - URL: `https://homedir.opensourcesantiago.io/comunidad`

2. **Verify Guest State** (If not logged in):
   - Check for the "Ingresar y unirme" button.
   - Click it and verify it redirects to the login/profile page.

3. **Verify Authenticated State** (Prerequisite: Log in):
   - **Case A: No GitHub Linked**:
     - Verify the button says "Vincular GitHub".
     - Click it and verify it starts the GitHub OAuth flow.
   - **Case B: GitHub Linked (Not yet a member)**:
     - Verify the button says "Unirme a la comunidad" (Primary Action).
     - **Action**: Click "Unirme a la comunidad".
     - **Verification**:
       - Page should reload with `?joined=true`.
       - Button should change to "Solicitud en proceso" and be disabled.
   - **Case C: Already a Member**:
     - Verify the button says "Ya eres miembro" (Disabled).
     - Verify the user appears in the "Directorio" list.

4. **Visual Check**:
   - Verify the "Directorio" list is visible.
   - Check for the "Miembro Oficial" badge (🏅) on valid members.
   - Verify the "Leaderboard" (Top Contributors) is displayed.
