# Phase 16 — publication evidence

Date: 2026-10-29.

## GitHub (published)

- Repository: <https://github.com/reystin/oneisle> (public, not a fork, default branch `main`)
- Head at publication: `17c17e84349bd4b3e924621181984f12ccf9e8cf`
- Tag: `v1.0.0+mc26.3-fabric` (annotated; object `a9caa79d57c38141697493205375653d9e725eab`,
  target `17c17e8`)
- Release: <https://github.com/reystin/oneisle/releases/tag/v1.0.0%2Bmc26.3-fabric>
  (published 2026-10-29T17:41:40Z, not a draft, not a prerelease)
- Assets:
  - `OneIsle-26.3-Fabric-1.0.0+mc26.3.jar` — 2,819,326 bytes
  - `OneIsle-26.3-Fabric-1.0.0+mc26.3-sources.jar` — 2,530,466 bytes

Both assets were downloaded again from the release and hashed:

```text
45a1cf04aa4deda5ae039f6b213542615046b3539a092bc7cd4177702cb9db22  OneIsle-26.3-Fabric-1.0.0+mc26.3.jar
ff85609f097a42fe3cfb9f595fab66a2b7b3c4dae1301c8ab8f9b5cc72fe3b4c  OneIsle-26.3-Fabric-1.0.0+mc26.3-sources.jar
```

The hashes match the local build (`sha256.txt` in `phase15-rename/`).

### Remotes after publication

`origin` = `https://github.com/reystin/oneisle.git`, `upstream` = the original
`https://github.com/y9hack337/OmniIsle.git`. Only `main` and the new tag were pushed. The
original repository's `pushedAt` is still `2026-08-19T14:51:40Z`; it was neither pushed to nor
modified, and no old tags were pushed to the new repository.

## Modrinth (not published)

- Slug `oneisle` is free (`GET /v2/project/oneisle` → 404 at the time of check).
- The stored token at `~/.config/modrinth/token` is rejected (`GET /v2/user` → 401), so no
  project or version was created. The token is never printed or stored in this repository.
- Required scopes for the next attempt: `PROJECT_CREATE`, `VERSION_CREATE`, `VERSION_READ`,
  `PROJECT_WRITE`.
- Ready-to-use drafts: `modrinth_project_body.md` (project body) and
  `modrinth_version_plan.md` (version payload fields, dependencies and upload rules).
- The old Modrinth project `omniisle` (`EaBXipIm`, owner `hack337`) was not touched.

Modrinth's rules prohibit direct reuploads without permission. OneIsle is a substantially changed
GPL-3.0 continuation under a new name with attribution, but moderation may still ask for the
original author's confirmation. Nothing here attempts to bypass moderation; if the project is
rejected, GitHub remains the distribution channel.

## Not verified

- Modrinth page/version metadata and file hash, because the project was not created.
- Interactive gameplay beyond the rename smoke (see `phase15-rename/README.md`).

## Files

| file | contents |
|---|---|
| `github_release.json` | public release metadata (name, tag, URL, assets) |
| `remote_refs.txt` | `git ls-remote --heads --tags origin` at verification time |
| `hashes.txt` | local and downloaded asset hashes |
| `modrinth_project_body.md` | draft body for the Modrinth project page |
| `modrinth_version_plan.md` | version metadata plan for `POST /v2/version` |
