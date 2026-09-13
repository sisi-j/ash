# Per-machine install, so the uninstaller cannot reach the depot

ash ships as a single NSIS installer that installs per-machine to `C:\Program Files\ash`. Its data — the depot, instances, accounts, logs — stays at `%LOCALAPPDATA%\ash`.

## Context

Tauri's NSIS bundler defaults to a per-user install, which needs no administrator prompt. That is the friendlier option for a game launcher, and it is what VS Code and Discord do.

It is also, for this product, a trap. The generated installer resolves a per-user install directory like this:

```nsis
StrCpy $INSTDIR "$LOCALAPPDATA\${PRODUCTNAME}"
```

`PRODUCTNAME` is `ash`, so that is `%LOCALAPPDATA%\ash` — the exact directory ash keeps the depot, the instances and the account list in. The uninstaller ends with `RMDir "$INSTDIR"`. Uninstalling would delete a multi-gigabyte depot and every world the player has.

## Decision

Install per-machine, to `$PROGRAMFILES64\ash`.

The two directories are then disjoint by construction, not by a rule someone has to remember. The uninstaller's reach is `$INSTDIR`, the shortcuts, the registry keys, and `$APPDATA\<bundle id>` — none of which is where ash keeps anything.

WebView2 is handled by the installer with `downloadBootstrapper`: it is present on current Windows, and where it is not, the installer fetches it. ash downloads several hundred megabytes of game content on first run regardless, so a small installer that fetches a runtime beats a 130MB one that embeds it.

## Consequences

- Installing prompts for administrator once. That is the cost, and it is the conventional behaviour for a desktop application on Windows — the official Minecraft launcher and Prism both do the same.
- Reinstalling or upgrading never touches the depot, so a player who reinstalls does not re-download the game. This is the acceptance criterion the decision exists to satisfy.
- The uninstaller's "delete application data" checkbox does nothing for ash. It removes `$APPDATA\com.ashlauncher.app`, which ash does not use — a 1GB depot does not belong in a roaming profile. Removing ash's data is deleting `%LOCALAPPDATA%\ash` by hand. This errs in the safe direction and should be revisited if a proper uninstall-everything flow is ever wanted.
- Renaming the product would move the install directory. It would not move the data directory, which is resolved in the Tauri adapter and named there. Those two facts are only unrelated because of this decision; before it, they were the same directory.

## Alternative considered

Keeping the per-user install and moving ash's data somewhere that cannot collide. Rejected because `%LOCALAPPDATA%\ash` is the correct, conventional home for a large local cache, and the collision is an accident of the product being named the same thing as its own folder — not a reason to give the data a worse address.
