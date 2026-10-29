# Modrinth version plan (not executed — token rejected 401)

## Project

- `slug`: `oneisle` (checked free: `GET /v2/project/oneisle` → 404)
- `title`: OneIsle
- `summary`: All biomes and structures on one configurable island for Fabric 26.3. Independent GPL-3.0 continuation of OmniIsle 0.5.0 by Hack337.
- `project_type`: `mod`
- `license_id`: `GPL-3.0-only`
- `source_url`: <https://github.com/reystin/oneisle>
- `issues_url`: <https://github.com/reystin/oneisle/issues>
- Categories: `worldgen`, `adventure`
- Environment: client `required`, server `required`
- Icon: `src/main/resources/assets/oneisle/icon.png`
- Gallery: `docs/screens/oneisle-1.0.0-smoke/en_*.png` (4 images; at most 8 allowed)
- Body: `modrinth_project_body.md`

## Version

- `name`: `OneIsle 1.0.0 for Minecraft 26.3`
- `version_number`: `1.0.0+mc26.3-fabric`
- `game_versions`: `["26.3"]`
- `loaders`: `["fabric"]`
- `release_channel`: `release`
- `featured`: false
- `status`: `listed`
- `project_id`: returned by `POST /v2/project`
- `file`: `OneIsle-26.3-Fabric-1.0.0+mc26.3.jar`
- `dependencies`: `P7dR8mSH` (Fabric API), `dependency_type: required`
- Do not upload the sources jar; source is on GitHub.
- Changelog: the 1.0.0 section of `CHANGELOG.md`.

## Required token

The token stored in `~/.config/modrinth/token` returned **401** on `GET /v2/user` on 2026-10-29.
A new PAT with scopes `PROJECT_CREATE`, `VERSION_CREATE`, `VERSION_READ`, `PROJECT_WRITE` must be
created at <https://modrinth.com/settings/pats> and written to that file. The token must not be
committed or printed.

## Moderation risk

Modrinth rules disallow direct reuploads without permission. This is a substantially modified
GPL-3.0 continuation under a new name with clear attribution and no endorsement claim, but
moderation may still request confirmation from the original author. Do not bypass moderation;
if the project is rejected, keep GitHub-only distribution and report the outcome.
