# Modrinth version plan (executed 2026-10-29)

## Project

- `slug`: `oneisle`; ID `SIvHqEPO`; team `q55xrs7h`; moderation thread `3PVBnxeb`
- `title`: OneIsle
- `summary`: All biomes and structures on one configurable island for Fabric 26.3. Independent
  GPL-3.0 continuation of OmniIsle 0.5.0 by Hack337.
- `project_type` request: `mod`; the draft response reported `project` before the version upload.
- `license_id`: `GPL-3.0-only`
- `source_url`: <https://github.com/reystin/oneisle>
- `issues_url`: <https://github.com/reystin/oneisle/issues>
- Categories: `worldgen`, `adventure`
- Icon: `src/main/resources/assets/oneisle/icon.png`, resized to a 512×512 JPEG (52,852
  bytes) for the 256 KiB icon limit; uploaded at creation.
- Gallery: only `docs/branding/oneisle-banner.jpg` (featured), per the “banner only” request.
- Body: `modrinth_project_body.md` (198 tests, 0 failures, 3 skips; timing measured).

## Version

- ID: `Oon3xljR`
- `name`: `OneIsle 1.0.0 for Minecraft 26.3`
- `version_number`: `1.0.0+mc26.3-fabric`
- `game_versions`: `["26.3"]`
- `loaders`: `["fabric"]`
- `version_type`: `release`
- `featured`: false
- `status`: `listed`
- `environment`: `client_and_server`
- `file`: `OneIsle-26.3-Fabric-1.0.0+mc26.3.jar`, 2,819,326 bytes
- `dependencies`: `P7dR8mSH` (Fabric API), `dependency_type: required`
- The sources jar was not uploaded; source is on GitHub.
- Changelog: the 1.0.0 section of `CHANGELOG.md`.

## Submission

- Project created as a draft; `PATCH /v2/project/SIvHqEPO {"requested_status":"approved"}` → 204.
- `https://modrinth.com/mod/oneisle` still renders “Project not found … may still be under
  review” on 2026-10-29. Wait for moderation; do not bypass it.
- The PAT has write scopes but no read scopes: `GET /v2/user` → 401, draft project/version
  reads → 404. This is a scope issue, not an invalid token.
- `PATCH client_side/server_side` → 401 “editing project”; the version’s
  `environment: client_and_server` is authoritative.

## Moderation risk

Modrinth rules disallow direct reuploads without permission. This is a substantially modified
GPL-3.0 continuation under a new name with clear attribution and no endorsement claim, but
moderation may still request confirmation from the original author. If the project is rejected,
keep GitHub-only distribution and report the outcome.
