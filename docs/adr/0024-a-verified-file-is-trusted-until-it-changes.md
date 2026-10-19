# A verified depot file is trusted until it changes on disk

Until now every check of an instance read and hashed every file it needs. For a 1.21.11 instance that's 4,661 files and about 700 MB. On the product owner's laptop, with the files not in memory, it took **26 seconds**. It ran when the Play page opened, gating LAUNCH GAME, and again on every launch. The product owner asked for a launcher that responds at once.

## Decision

- **A file's hash is checked once.** The depot records, in `depot/verified.json`, each file's hash, size and modification time when it was last verified, or when a download wrote it, having hashed it on the way in.
- **After that it is trusted while its size and modification time are unchanged.** Anything that writes the file changes its modification time, whether a download, an antivirus restoring it, or a player copying over it. Its hash is then checked again on the next check.
- **A metadata document with a published hash is read from its verified copy** instead of being fetched again: the version metadata, the asset index and the loader document. It can't change at its address. The version catalogue is refreshed as before.
- **The account's Minecraft session is reused for 20 hours** (`Config::session_reuse`) instead of walking the six-request Microsoft chain on every launch. Mojang's tokens last 24 hours. The session is kept in memory only, as the token always has been, and dropped when the account signs out.

## Consequences

- **The same check takes 0.11 seconds** (1.21.11) and 0.03 (1.8.9). LAUNCH GAME also takes a click while the check is still running.
- **A file corrupted in place while keeping its size and modification time goes unnoticed** until it is next written. That's bit rot, or a tool that restores timestamps. The game would fail on that file rather than ash catching it first. Deleting `verified.json` makes the next check hash everything; a "Repair files" action would do the same.
- **A session revoked on Microsoft's side** (a password change, say) still launches the game for up to 20 hours. The game then reports an invalid session when joining a server, as the official launcher's cached session would. Signing out and in again clears it.
- **Measured with `examples/check-speed.rs`** against a real depot, so the claim can be checked again.
