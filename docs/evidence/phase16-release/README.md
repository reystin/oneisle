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

## Modrinth (submitted for review, not public yet)

- Project: slug `oneisle`, ID `SIvHqEPO`, team `q55xrs7h`, moderation thread `3PVBnxeb`;
  title OneIsle, license GPL-3.0-only, categories worldgen/adventure, source and issues on
  GitHub.
- Created as a draft (`status: draft`, `requested_status: approved`) and submitted with
  `PATCH /v2/project/SIvHqEPO {"requested_status":"approved"}` → 204. On 2026-10-29
  <https://modrinth.com/mod/oneisle> still renders “Project not found … may still be under
  review”, so the listing must not be described as live until that page resolves.
- Banner: `docs/branding/oneisle-banner.jpg` (2000×1000, SHA-256
  `848a7d18f3e320fdbca15548a80dc36943ad76e7e0b60af8e171f272eaa6a851`) uploaded with
  `POST /v2/project/SIvHqEPO/gallery?ext=jpg&featured=true` → 204. It is the only gallery
  image (“banner only”). The project icon is the existing `assets/oneisle/icon.png`, resized
  to a 512×512 JPEG (52,852 bytes) to fit the 256 KiB icon limit.
- Version: `Oon3xljR`, `1.0.0+mc26.3-fabric`, release, game `26.3`, loader `fabric`,
  environment `client_and_server`, required dependency Fabric API `P7dR8mSH`; jar 2,819,326
  bytes. Modrinth sha512/sha1 match the local release jar (see `hashes.txt`).
- Auth: the PAT has write scopes but no read scopes. `GET /v2/user` → 401,
  `GET /v2/project/...` and `GET /v2/version/...` → 404 for the draft. `PATCH` of the
  deprecated `client_side`/`server_side` fields returns 401 “editing project”; the version’s
  `environment: client_and_server` is the value that matters. Full log:
  `modrinth_publish.txt`; no token value is recorded.
- The old Modrinth project `omniisle` (`EaBXipIm`, owner `hack337`) was not touched.

Modrinth's rules prohibit direct reuploads without permission. OneIsle is a substantially changed
GPL-3.0 continuation under a new name with attribution, but moderation may still ask for the
original author's confirmation. Nothing here attempts to bypass moderation; if the project is
rejected, GitHub remains the distribution channel.

## Not verified

- Modrinth public page, version metadata and CDN file hash cannot be checked until the project
  leaves review; API reads are unavailable without read scopes.
- Interactive gameplay beyond the rename smoke (see `phase15-rename/README.md`).

## Files

| file | contents |
|---|---|
| `github_release.json` | public release metadata (name, tag, URL, assets) |
| `remote_refs.txt` | `git ls-remote --heads --tags origin` at verification time |
| `hashes.txt` | local and downloaded asset hashes |
| `modrinth_project_body.md` | project body that was published |
| `modrinth_version_plan.md` | version metadata plan for `POST /v2/version` |
| `modrinth_project.json` | project ID/status/categories/icon/gallery at creation |
| `modrinth_version.json` | version metadata, dependency and file hashes |
| `modrinth_publish.txt` | publication log, banner upload, auth and moderation notes |
