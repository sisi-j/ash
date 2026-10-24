# Research 0014: Modrinth, as ash uses it

24 October 2026, for #144 (spec 0005, ADR-0026). Each finding here comes from asking Modrinth's live API, `api.modrinth.com/v2`. The answers are recorded, trimmed, in `launcher/core/tests/fixtures/modrinth/`.

## 1. Asking

- **User-Agent.** Modrinth asks every client to identify itself uniquely. ash sends `sisi-j/ash/<version> (+https://github.com/sisi-j/ash)`. It names the repository, not a person: nobody's email goes to a third party.
- **Rate limit:** 300 requests a minute per IP address. Every answer reports it in `X-Ratelimit-Limit`, `-Remaining` and `-Reset`. A search per keystroke, debounced, is far inside that.
- **No key** is needed for anything ash does: searching, reading versions, and downloading.

## 2. Searching for an instance

`GET /v2/search?query=…&facets=…&index=relevance|downloads&limit=20&offset=…`

The facets are a JSON list of lists: ANDed across lists, ORed within one. For an instance, ash always sends three:

| Instance | Facets |
| --- | --- |
| 1.21.11 or 1.20.4, Fabric | `[["project_type:mod"],["versions:<version>"],["categories:fabric"]]` |
| 1.8.9, Legacy Fabric | `[["project_type:mod"],["versions:1.8.9"],["categories:legacy-fabric"]]` |

- **Loaders are categories.** Modrinth's loader tags (`/v2/tag/loader`) include `fabric` and `legacy-fabric`, and a project's loaders appear among its `categories`. Searching "sodium" on 1.21.11 Fabric finds 45 projects. 1.8.9 Legacy Fabric has 67 projects in all.
- **Each hit has** `project_id`, `slug`, `title`, `description`, `icon_url` (on `cdn.modrinth.com`), `downloads` and `categories`. That's enough for a result row without asking again.
- With no query, sorting by `downloads` gives a sensible "popular" list for an empty search box.

## 3. Choosing a version

`GET /v2/project/<id or slug>/version?loaders=["fabric"]&game_versions=["1.21.11"]` returns only versions for that loader and game version, **newest first**. Each version has:
- `id`, `version_number` and `version_type` (`release`, `beta` or `alpha`), and `date_published`;
- `files`, with exactly one `primary: true`. Each file has its `url` (on `cdn.modrinth.com`), `filename`, `size`, and `hashes` with `sha512` and `sha1`;
- `dependencies`, each with `project_id`, an optional `version_id`, and `dependency_type`: `required`, `optional`, `incompatible` or `embedded`.

**Pre-releases are common enough to matter.** Sodium's newest version for 1.21.11 is `0.8.15-beta.1`. Its newest release is `0.8.14`.

So ash picks:
1. **For a dependency that names a `version_id`:** that exact version, from `GET /v2/version/<id>`. Iris 1.10.8 names Sodium `0.8.14` this way.
2. **Otherwise:** the newest `release`.
3. **If there's no release at all:** the newest version of any type, and the Mods page says it's a beta or alpha.

## 4. Dependencies

- **`required`** is installed, recursively, by the same rules. A project already in the instance is kept. The exception is a dependency that pins a different version: that version replaces it, and the page says so.
- **`optional`** is listed on the mod, not installed.
- **`incompatible`** stops the install when that project is in the instance, naming it.
- **`embedded`** is already inside the mod's jar, so there's nothing to do.

## 5. ash's bundled mods

| On Modrinth | Project id | What ash does with a dependency on it |
| --- | --- | --- |
| Fabric API (`fabric-api`) | `P7dR8mSH` | Nothing. ash bundles the whole of Fabric API on 1.21.11 and 1.20.4, so it's already met. |
| Legacy Fabric API (`legacy-fabric-api`) | `9CJED7xi` | **Installs it.** Modrinth's file is the full API (`legacy-fabric-api-1.13.5.jar`, 2 MB, all modules), and ash bundles only the aggregator and 4 of its 44 modules (glossary: Bundled mod). A mod needing others needs the full jar. ADR-0018 already lets a player's copy stand in for ash's, and the load report says when it does. |

Modrinth's Legacy Fabric API 1.13.5 is the same version ash pins. So the full jar adds the modules ash doesn't ship and changes none it does.

## 6. Downloading

- Files come from `cdn.modrinth.com`. Each is checked against `hashes.sha512` before it's moved into the `mods` folder. A mismatch is a failed install, and nothing is left behind.
- Icons are on `cdn.modrinth.com` too. The launcher's content security policy needs that host under `img-src` to show them.

## 7. What this doesn't cover

- **Updates:** the versions endpoint already answers "is there a newer one". `ash-mods.json` records the version ids to compare against.
- **Modpacks** (`.mrpack`) and **CurseForge:** out of scope (spec 0005).
