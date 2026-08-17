# Speaker Photo Compatible URLs

Policy for speaker and sponsor photos: **100% local storage**. Photos are uploaded
and served by Homedir itself; the browser never loads images from external domains.

## How it works

- Speakers upload their photo from their profile (`/private/profile` → Speaker Profile → upload).
  Files are stored in `data/uploads/speakers/` and served from `/speaker/{id}/photo`.
- When no local upload exists but a `photoUrl` is configured, the
  `SpeakerPhotoProxyService` downloads the image **server-side**, optimizes it
  (max 400px, JPEG quality 0.85) and caches it locally with a 7-day TTL. The
  browser always loads the cached local copy from `/speaker/{id}/photo`.
- If nothing is available, a generated default avatar is returned.

## Compatible photo URL domains (proxy allow-list)

The server-side proxy may fetch from:

- `avatars.githubusercontent.com`, `githubusercontent.com` (GitHub avatars)
- `gravatar.com`, `www.gravatar.com`, `secure.gravatar.com`
- `cloudinary.com`, `res.cloudinary.com`
- `imgur.com`, `i.imgur.com`
- `lh3.googleusercontent.com` (temporary fallback while migrating)
- `drive.google.com` (migration only — view URLs like
  `https://drive.google.com/file/d/{id}/view?usp=sharing` are converted to the
  direct image endpoint `https://drive.google.com/uc?export=view&id={id}`)

## Not supported

- **Google Drive view pages** (`drive.google.com/file/d/.../view`): they are HTML
  pages, not images. Do **not** paste them as `photoUrl`.
- Any domain not in the allow-list above (blocked with a `Rejected photo URL`
  log warning).
- Non-HTTPS URLs and private/local IP addresses (SSRF protection).

## CSP

The `img-src` directive only allows `'self'` (plus a few trusted image hosts like
`cdn.simpleicons.org`). `drive.google.com` and `lh3.googleusercontent.com` were
**removed** from `CspHeaderFilter` once all photos started being served through
the local proxy — the browser no longer needs direct access to them.

## Migration

Existing `photoUrl` values pointing to Google Drive are handled automatically by
the proxy: the URL is rewritten to the direct download endpoint, fetched,
optimized and cached locally. No manual data migration is required; speakers are
encouraged to upload their photo from their profile for a permanent local copy.
