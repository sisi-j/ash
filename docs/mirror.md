# ash's mirror of the Legacy Fabric artifacts

## Why there is one

Legacy Fabric is a single self-hosted repository behind Cloudflare with no
published mirror, maintained by one person who authored 69 of 69 commits to
the API repository over twelve months. The cadence is real and current; the
exposure is that there is exactly one copy of everything ash's 1.8.9 support
needs.

What ash needs is small — about 5.5MB — so mirroring converts a
project-dormancy risk into nothing. It is cheap now and impossible later,
which is the whole argument.

## Where it lives

<https://github.com/sisi-j/ash/releases/tag/mirror-2026-09-16>

A GitHub Release on this repository, marked as a pre-release so it is never
shown as ash's latest release. The assets are flat — there is no Maven
layout — so each pin names the whole URL rather than deriving one.

**`+` becomes `-` in asset names.** GitHub mangles some characters in release
asset names, and a mirrored file that cannot be fetched under the name ash
expects is a mirror that does not work. `lwjgl-2.9.4+legacyfabric.17.jar` is
stored as `lwjgl-2.9.4-legacyfabric.17.jar`.

| Asset | Coordinate |
| --- | --- |
| `intermediary-1.8.9.jar` | `net.legacyfabric:intermediary:1.8.9` |
| `lwjgl-2.9.4-legacyfabric.17.jar` | `org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17` |
| `lwjgl_util-2.9.4-legacyfabric.17.jar` | `org.lwjgl.lwjgl:lwjgl_util:2.9.4+legacyfabric.17` |
| `lwjgl-platform-2.9.4-legacyfabric.17-natives-windows.jar` | the same, `natives-windows` |
| `lwjgl-platform-2.9.4-legacyfabric.17-natives-osx.jar` | the same, `natives-osx` |
| `legacy-fabric-api-1.13.5-1.8.9.jar` | `net.legacyfabric.legacy-fabric-api:legacy-fabric-api:1.13.5+1.8.9` |
| `NOTICE.md` | attribution, see below |

**Upstream Fabric's own artifacts are deliberately not mirrored.** The loader
jar and the loader's launcher metadata come from `maven.fabricmc.net`, a
well-resourced project with documented fallback hosts of its own. What ash
mirrors is the single self-hosted server, not everything it downloads.

The Linux natives are not mirrored either. `version.rs` selects natives for
Windows and macOS only, so a Linux jar would be four megabytes nothing could
ever unpack — and `PinnedNative` takes an `Os` rather than a string so that
one cannot be added by accident.

## How ash uses it

Upstream first; the mirror only when upstream **cannot be reached** — a
transport failure or a failing status. A file that arrives and fails its hash
is not a reachability problem, and asking somewhere else would turn a corrupt
upstream artifact into a silent success.

**The mirror is a second copy, not a second authority.** Every artifact is
verified against the same SHA-1 pinned in `launcher/core/src/loader.rs`
whichever source it came from, so a mirror that had been tampered with, or had
simply drifted, cannot put anything into the depot. `legacy_fabric.rs` proves
both halves: preparation succeeds with the whole Legacy Fabric host blocked,
and a mirror serving the wrong bytes is refused.

## Licensing

All three upstreams permit redistribution, which is why this mirror is
lawful:

- **LWJGL 2 fork** — BSD-3-Clause. The licence lives at `doc/LICENSE` in
  `Legacy-Fabric/lwjgl` rather than at the repository root, which is why
  GitHub's licence detection reports that repository as unlicensed. It is
  not. BSD-3 requires the copyright notice and disclaimer to be reproduced
  with any binary redistribution, which is what `NOTICE.md` in the release is
  for.
- **Legacy Fabric API** — Apache-2.0.
- **Intermediary** — CC0-1.0, a public-domain dedication.

`NOTICE.md` must ship with every refresh. It is not optional decoration; it
is the condition on which two of these three may be redistributed at all.

## Refreshing it

Needed when a pin in `loader.rs` moves to a new version. The pins are the
source of truth: the script below verifies every downloaded byte against a
hash already in `loader.rs` and refuses to proceed otherwise, so update the
pins first.

1. Update the pin in `launcher/core/src/loader.rs`, including its hash and
   size.
2. Update the coordinate in `ARTIFACTS` in `scripts/refresh-mirror.py`. A
   version bump always changes it, because the version is part of the Maven
   path; the asset name and the URL are both derived from it.
3. Fetch and verify:

   ```
   python scripts/refresh-mirror.py <output-directory>
   ```

   It matches each artifact to *its own* pin by coordinate, writes nothing
   under its real name until it has passed, and exits non-zero if anything
   did not match. A run that fails leaves no publishable file behind.
4. Copy `NOTICE.md` from the previous release into the output directory and
   update it for anything that changed.
5. Publish, with today's date as the tag:

   ```
   gh release create mirror-YYYY-MM-DD \
     --title "Mirror: Legacy Fabric artifacts (YYYY-MM-DD)" \
     --prerelease \
     --notes "..." \
     <output-directory>/*
   ```
6. Bump the base URL in the `mirrored!` macro in `loader.rs` to the new tag.
   It is one line, and it is the only place the tag appears in ash.
7. Leave the old release in place. An ash build that is already out there
   still points at it.

## Checking it is intact

```
python scripts/refresh-mirror.py <tmp>   # hashes upstream against the pins
gh release view mirror-2026-09-16 --json assets \
  --jq '.assets[] | "\(.name)\t\(.size)"'
```

The sizes should match what `loader.rs` pins. To check the mirror itself
rather than upstream, fetch an asset and hash it:

```
curl -sSL https://github.com/sisi-j/ash/releases/download/mirror-2026-09-16/lwjgl-2.9.4-legacyfabric.17.jar | sha1sum
```
