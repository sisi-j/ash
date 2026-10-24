// ash's version and its release notes (ADR-0025): `node scripts/release.mjs <command>`.
//
//   check            every copy of the version agrees with tauri.conf.json, and
//                    CHANGELOG.md has a section for it
//   version          prints the version
//   status           prints "release" when the version's changelog section is
//                    dated, or "unreleased" when it says so - CI publishes only
//                    the first
//   notes            prints the version's changelog section, for the release
//                    and the launcher's "what's new"
//   latest-json <url> <signature-file>
//                    prints the updater's latest.json for the installer at <url>
//
// The version lives in launcher/src-tauri/tauri.conf.json. The other copies
// exist because each build tool reads its own file; this keeps them honest.

import { readFileSync } from "node:fs";

const root = new URL("../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root), "utf8");

const version = JSON.parse(read("launcher/src-tauri/tauri.conf.json")).version;

const copies = {
  "Cargo.toml [workspace.package]": /^\[workspace\.package\][^[]*?^version = "([^"]+)"/ms.exec(read("Cargo.toml"))?.[1],
  "launcher/package.json": JSON.parse(read("launcher/package.json")).version,
  "launcher/package-lock.json": JSON.parse(read("launcher/package-lock.json")).version,
  "client/gradle.properties mod_version": /^mod_version=(.+)$/m.exec(read("client/gradle.properties"))?.[1]?.trim(),
};

/** The version's section of CHANGELOG.md: its heading's date (or null) and its text. */
function section() {
  const lines = read("CHANGELOG.md").split(/\r?\n/);
  const start = lines.findIndex((line) => line.startsWith(`## ${version} `) || line === `## ${version}`);
  if (start === -1) return null;
  const heading = lines[start];
  let end = lines.findIndex((line, i) => i > start && line.startsWith("## "));
  if (end === -1) end = lines.length;
  return {
    date: /(\d{4}-\d{2}-\d{2})\s*$/.exec(heading)?.[1] ?? null,
    unreleased: /unreleased/i.test(heading),
    text: lines.slice(start + 1, end).join("\n").trim(),
  };
}

function fail(message) {
  console.error(message);
  process.exitCode = 1;
}

const [command, ...args] = process.argv.slice(2);
switch (command) {
  case "check": {
    const wrong = Object.entries(copies).filter(([, v]) => v !== version);
    for (const [where, v] of wrong) fail(`${where} says ${v}, but tauri.conf.json says ${version}.`);
    const notes = section();
    if (notes === null) fail(`CHANGELOG.md has no "## ${version}" section.`);
    else if (notes.date === null && !notes.unreleased) {
      fail(`CHANGELOG.md's ${version} heading needs a date (YYYY-MM-DD) or "(unreleased)".`);
    } else if (notes.text === "") fail(`CHANGELOG.md's ${version} section is empty.`);
    if (process.exitCode !== 1) console.log(`${version}: every copy agrees, and CHANGELOG.md has its section.`);
    break;
  }
  case "version":
    console.log(version);
    break;
  case "status": {
    const notes = section();
    console.log(notes !== null && notes.date !== null && !notes.unreleased ? "release" : "unreleased");
    break;
  }
  case "notes": {
    const notes = section();
    if (notes === null) fail(`CHANGELOG.md has no "## ${version}" section.`);
    else console.log(notes.text);
    break;
  }
  case "latest-json": {
    const [url, signatureFile] = args;
    const notes = section();
    console.log(
      JSON.stringify(
        {
          version,
          notes: notes?.text ?? "",
          pub_date: new Date().toISOString(),
          platforms: { "windows-x86_64": { signature: readFileSync(signatureFile, "utf8").trim(), url } },
        },
        null,
        2,
      ),
    );
    break;
  }
  default:
    fail("usage: node scripts/release.mjs check | version | status | notes | latest-json <url> <signature-file>");
}
