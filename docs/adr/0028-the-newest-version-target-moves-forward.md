# The newest version target moves forward

ash's version targets are **1.8.9, 1.20.4, 1.21.11 and the newest Minecraft release**, which is 26.3 at the start of the beta. When Minecraft releases a new version, ash ports its client to it, and that becomes the newest target. The one before is **retired**: its last client build keeps shipping unchanged, and it gets no new features.

## Context

A player on the current game had none of ash's features: the client covered 1.8.9, 1.20.4 and 1.21.11, and Minecraft was on 26.3 (spec 0007). Each version target is its own build and its own maintenance (ADR-0005, ADR-0015), so supporting every release forever isn't possible. Dropping a version is a broken promise to everyone with an instance on it.

The three older targets are where players stay for years: 1.8.9 for PvP, 1.20.4 and 1.21.11 for the servers that settle on them. The newest release is where everyone else is, and it changes every few months.

## Decision

- **Four targets:** 1.8.9, 1.20.4 and 1.21.11, which stay, and the newest release, which moves.
- **A new release:** ash ports its client to it within a few weeks, as for 26.3 (research 0016, #177), and it becomes the newest target. The first run makes an instance on it, newest first.
- **The retired target:** the release before is marked retired in its loader pin.
  - Its last client build keeps shipping in the installer, unchanged, and its instances prepare and launch exactly as they did, with the features they had.
  - It gets no new features: a beta or later phase builds for the four current targets only.
  - Its instances say so: "ash's newest features need 26.4", with a button that makes an instance on the newest target. Nothing is moved or deleted for the player.
- **The newest target** is the first pin, newest first, that has ash's client in it and isn't retired.

## Consequences

- ash maintains four client builds at any time, plus retired builds that are only ever shipped, never changed. A retired build that breaks - a Fabric or Mojang change underneath it - is fixed only if it's cheap; otherwise its note is how a player learns to move.
- The installer grows by one client jar per retired target. They're small, so it's not a concern for some years.
- Spec 0006 and 0007's "26.3" mean "the newest target" wherever they talk about features: a feature that lands after 26.4's port lands on 26.4, not 26.3.
- The glossary's *version target* entry says this (CONTEXT.md).
