// A test installer: the beta switch on (spec 0006, ADR-0029).
//
//   node scripts/test-installer.mjs      (or `npm run build:test-installer`
//                                         from launcher/)
//
// Builds ash's client jars, then the installer with `ASH_BETA=1`, so the
// launcher shows the beta phases' work and tells the game to run it. Never
// published: a release is built with the switch off, by CI's `installed`
// job, which refuses an installer that has it on. The installer lands where
// `npm run build:installer` puts it, under target/release/bundle/nsis/.

import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));

function run(command, args, cwd, env = process.env) {
  // A shell, so Windows finds `npm.cmd` and `gradlew.bat` by their bare names.
  const result = spawnSync(command, args, { cwd, env, stdio: "inherit", shell: true });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

run(process.platform === "win32" ? "gradlew.bat" : "./gradlew", ["assemble"], `${root}client`);
run("npm", ["run", "build:installer"], `${root}launcher`, { ...process.env, ASH_BETA: "1" });
console.log("\nA test build, with the beta switch on, in target/release/bundle/nsis/.");
