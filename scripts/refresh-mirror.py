"""Fetch the Legacy Fabric artifacts ash mirrors, and verify them.

The expected hashes are not retyped here. They are read out of
`launcher/core/src/loader.rs` and matched to each artifact *by coordinate*,
so this checks every byte against the hash ash will actually verify at
prepare time - not against a second list that could drift from it, and not
merely against "some hash in the file", which would pass if two artifacts
were swapped.

Nothing is written under its real name until it has passed. See
`docs/mirror.md` for what this is for and how to publish the result.

    python scripts/refresh-mirror.py <output-directory>
"""

import hashlib
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
LOADER = os.path.join(ROOT, "launcher", "core", "src", "loader.rs")

LEGACY = "https://maven.legacyfabric.net/"

# (coordinate, classifier or None). The asset name and the Maven path are both
# derived, so adding an artifact means adding one line here and a pin in
# loader.rs - and the two are checked against each other below.
ARTIFACTS = [
    ("net.legacyfabric:intermediary:1.8.9", None),
    ("org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17", None),
    ("org.lwjgl.lwjgl:lwjgl_util:2.9.4+legacyfabric.17", None),
    ("org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17", "natives-windows"),
    ("org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17", "natives-osx"),
    ("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:1.13.5+1.8.9", None),
]


def maven_path(coordinate, classifier=None):
    group, artifact, version = coordinate.split(":")
    suffix = "-%s" % classifier if classifier else ""
    return "%s/%s/%s/%s-%s%s.jar" % (
        group.replace(".", "/"),
        artifact,
        version,
        artifact,
        version,
        suffix,
    )


def asset_name(coordinate, classifier=None):
    """The flat name the mirror stores it under.

    `+` becomes `-` because GitHub mangles some characters in release asset
    names, and a mirrored file that cannot be fetched under the name ash
    expects is a mirror that does not work.
    """
    return maven_path(coordinate, classifier).rsplit("/", 1)[-1].replace("+", "-")


def pinned_hashes(source):
    """Every hash in loader.rs, keyed by what it belongs to.

    A `sha1` belongs to the nearest `classifier` above it when there is one
    below the nearest `name`, and to that `name` otherwise - which is exactly
    how the nested `PinnedNative` blocks are laid out.
    """
    fields = re.finditer(
        r'(?P<kind>name|classifier|sha1):\s*"(?P<value>[^"]*)"', source
    )
    hashes, coordinate, classifier = {}, None, None
    for field in fields:
        kind, value = field.group("kind"), field.group("value")
        if kind == "name":
            coordinate, classifier = value, None
        elif kind == "classifier":
            classifier = value
        elif coordinate is not None and re.fullmatch(r"[0-9a-f]{40}", value):
            hashes[(coordinate, classifier)] = value
    return hashes


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: python scripts/refresh-mirror.py <output-directory>")
    out = sys.argv[1]

    with open(LOADER, encoding="utf-8") as handle:
        hashes = pinned_hashes(handle.read())
    if not hashes:
        sys.exit("no pinned hashes found in loader.rs - refusing to verify against nothing")

    os.makedirs(out, exist_ok=True)
    failures = []

    for coordinate, classifier in ARTIFACTS:
        name = asset_name(coordinate, classifier)
        expected = hashes.get((coordinate, classifier))
        if expected is None:
            print("%-56s NOT PINNED in loader.rs" % name)
            failures.append(name)
            continue

        url = LEGACY + maven_path(coordinate, classifier).replace("+", "%2B")
        # Written under a temporary name and only renamed once it has passed,
        # so a failed run can never leave something publishable behind.
        partial = os.path.join(out, name + ".part")
        subprocess.run(["curl", "-sSL", "--max-time", "180", url, "-o", partial], check=True)

        with open(partial, "rb") as handle:
            body = handle.read()
        digest = hashlib.sha1(body).hexdigest()

        if digest != expected:
            print("%-56s %9d bytes  sha1=%s  *** expected %s ***"
                  % (name, len(body), digest, expected))
            os.remove(partial)
            failures.append(name)
            continue

        os.replace(partial, os.path.join(out, name))
        print("%-56s %9d bytes  sha1=%s  matches its pin" % (name, len(body), digest))

    if failures:
        sys.exit("refusing to publish; these did not match their pin: %s" % ", ".join(failures))
    print("\nall %d artifacts match the hash ash pins for them" % len(ARTIFACTS))
    print("remember to copy NOTICE.md into %s before publishing" % out)


if __name__ == "__main__":
    main()
