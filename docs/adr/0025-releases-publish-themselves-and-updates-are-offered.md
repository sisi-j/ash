# Releases publish themselves, and updates are offered

A merge to `main` that changes ash's version is a release. CI builds it, tests the real installer, and publishes it to GitHub Releases with nobody pressing a button. Installed copies of ash find it through Tauri's updater, check its signature, and offer it to the player with what's new.

## Context

The alpha (spec 0005) goes to testers who aren't developers, and some of them the product owner doesn't know. Fixes have to reach them without anyone sending installers around, and a bad release must not reach them at all. Three choices were put to the product owner:
- **Publishing on approval:** CI builds a draft and a person publishes it.
- **Publishing automatically:** chosen.
- **Updating:** offered with what's new, or installed silently. "Offered" was chosen.

## Decision

- **The version lives in `tauri.conf.json`,** and CI keeps every other copy of it in step. `CHANGELOG.md` has a section for each version, and that section becomes the update's "what's new".
- **A dated version with no release yet is released.** A version is ready when its `CHANGELOG.md` heading carries a date. `(unreleased)` in the heading holds it back, so a version can sit on `main` while the work for it lands.
- **The release is the installer every check passed.** The installed-app job builds it, signs the update files, installs it and runs its self-check. The release job runs only after every job in the workflow has passed, and publishes that installer, its signature and `latest.json`.
- **Any failure publishes nothing.**
- **The checks are the only gate.** That is the cost of automatic publishing, accepted by the product owner. Because the version has to change and be dated, an ordinary merge never releases.
- **Alpha releases are ordinary releases, not GitHub pre-releases.** The updater and the website's download link both read "latest", and GitHub's "latest" skips pre-releases. The "alpha" is in the version number (`0.1.0-alpha.N`).
- **Updates are signed with ash's own key,** checked against the public key built into every copy. The private key is a CI secret, with an offline copy kept by the product owner.
- **Updating is the player's choice.** A bar offers the update when ash starts. It can't install while a game ash started is running, and a failed check is silent.

## Consequences

- A fix reaches every tester who opens ash after the merge, with no manual step.
- A release that starts but misbehaves gets through the gate. The remedy is the next version, which is one merge away.
- Losing the update key strands every installed copy on its version. Testers would have to reinstall by hand.
- Windows still warns about the unsigned installer on first install. Updates install through the updater, without the warning.
