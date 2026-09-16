//! The mod-loading layer an instance runs under, and what ash installs for it.
//!
//! Vanilla is a loader here, not the absence of one. That is the whole of
//! this module's reason to exist: "exactly one loader" is then true of every
//! instance, and preparing, launching and the UI never grow an "is there a
//! loader at all" branch that would have to be repeated at each of them.
//!
//! The loader is chosen when an instance is created and never afterwards.
//! Paths, the classpath and the main class all follow from it, so changing
//! one in place would mean rebuilding an instance around a game directory
//! full of a different loader's mods.
//!
//! # Why everything here is pinned
//!
//! The ordinary way to install Fabric is to ask `meta.fabricmc.net` for a
//! profile JSON and hand it to the launcher. ash cannot do that and keep the
//! promise the depot already makes, because **that document is generated per
//! request** - its `releaseTime` is the server's clock at the moment of
//! asking - so there is no published hash for it and there cannot be one.
//! Without a hash, "the network is down" becomes the way to make ash run
//! whatever happens to be in its cache.
//!
//! So ash pins a loader version per version target, fetches the loader's own
//! immutable launcher metadata, and assembles the profile itself. Fabric Meta
//! is not in the launch path at all. See ADR-0014.
//!
//! The hashes below are the trust anchor and are recorded here rather than
//! fetched, so a prepare is verifiable from the very first one and needs no
//! sidecar request. Updating a pin is a deliberate act in a release, which is
//! what ADR-0014 says it should be; see `docs/adr/0014-loader-metadata-pinned-not-fetched.md`.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::version::Os;

/// The mod-loading layer an instance runs under.
///
/// Deliberately not a string. A loader ash cannot prepare is an instance that
/// can never launch, and the set of loaders is small, closed and ash's own -
/// so it is a type the compiler checks rather than a value to validate.
///
/// Deliberately no `Default`, either. A defaultable loader is a loader that
/// can be left unsaid, and `..Default::default()` would silently mean vanilla
/// now that a second loader exists - which is the special case this type was
/// introduced to prevent. Reading an instance that predates loaders is the
/// one place a loader is not named, and it names its own default there.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Loader {
    /// The game as Mojang ships it, with no mod loader and no ash client.
    Vanilla,
    /// Fabric Loader, on the modern version target.
    Fabric,
    /// Fabric Loader again - the *same* upstream artifact, unmodified - on
    /// 1.8.9. Legacy Fabric's own meta service serves
    /// `net.fabricmc:fabric-loader`, not a fork of it; there is no "Legacy
    /// Fabric Loader" artifact, and looking for one is looking for a file
    /// that does not exist.
    ///
    /// Its own loader rather than a flag on [`Loader::Fabric`] because what
    /// differs is most of what a pin names: the intermediary, the API, and
    /// an LWJGL 2 fork that replaces the game's own.
    LegacyFabric,
}

impl Loader {
    /// Every loader ash knows, in the order to offer them.
    ///
    /// Which of these a given version target can actually run is a different
    /// question, and [`loaders_for`] is the one that answers it.
    pub const ALL: [Loader; 3] = [Loader::Vanilla, Loader::Fabric, Loader::LegacyFabric];

    /// The machine name, matching how the loader is written to disk.
    ///
    /// For an error's `Display` and ash's own log, never for a player: what
    /// a loader is called in front of one is the UI's wording to choose.
    pub(crate) fn name(self) -> &'static str {
        match self {
            Loader::Vanilla => "vanilla",
            Loader::Fabric => "fabric",
            Loader::LegacyFabric => "legacy_fabric",
        }
    }
}

// ---- what a loader is made of ----------------------------------------------

/// One file ash has pinned: where it lives and what it must hash to.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PinnedFile {
    pub url: &'static str,
    pub sha1: &'static str,
    pub size: u64,
}

/// A library ash adds to the ones the loader's own metadata names.
///
/// These are the entries Fabric Meta would write bare, as `{name, url}` with
/// no hash - it has no more information about them than their coordinate.
/// ash pins the hash instead, so every library on a modded classpath is
/// verified on the same terms as a vanilla one.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PinnedLibrary {
    /// Maven coordinate: `group:artifact:version` with an optional
    /// `:classifier`.
    pub name: &'static str,
    /// The Maven repository root it is served from, trailing slash included.
    pub repository: &'static str,
    /// Where else the identical bytes can be had, if anywhere.
    ///
    /// Not a Maven root but the whole URL, because ash's mirror is a flat
    /// store rather than a repository layout. `None` where the upstream is
    /// one nobody worries about; see `docs/mirror.md` for which are
    /// mirrored and why.
    pub mirror: Option<&'static str>,
    pub sha1: &'static str,
    pub size: u64,
    /// The platform jars this library unpacks, if it is one that has to be.
    ///
    /// Empty for an ordinary jar, and then `sha1` and `size` describe the
    /// one artifact the coordinate names. When it is not empty the
    /// coordinate names no single jar at all - only these do - which is how
    /// Mojang's own 1.8.9 metadata is shaped too.
    pub natives: &'static [PinnedNative],
}

/// One platform's native jar for a library that has to be unpacked.
///
/// A 1.8.9-era library keeps its platform code in separate classifier jars
/// rather than in the one the classpath names. LWJGL 2 loads those off
/// `java.library.path`, so they have to become real files on disk; LWJGL 3
/// reads them straight out of the classpath and needs none of this.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PinnedNative {
    /// Where else the identical bytes can be had. See
    /// [`PinnedLibrary::mirror`].
    pub mirror: Option<&'static str>,
    /// The platform this jar is for.
    ///
    /// [`Os`] rather than the string Mojang's metadata uses, so a pin cannot
    /// name a platform ash does not select for. `version.rs` leaves Linux
    /// out deliberately - shipping it would mean shipping untested
    /// native-library selection - and a `&str` here would let a pin carry
    /// four megabytes of natives that nothing could ever unpack.
    pub os: Os,
    /// The Maven classifier carrying that platform's jar.
    pub classifier: &'static str,
    pub sha1: &'static str,
    pub size: u64,
}

/// Everything ash needs to install one loader on one version target.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LoaderPin {
    /// Which loader this describes.
    pub loader: Loader,
    /// The version target it is pinned for. A loader is pinned per target
    /// rather than once: what differs between targets is the intermediary,
    /// the mappings and the API, which is most of what a pin names.
    pub version_id: &'static str,
    /// The Fabric Loader version installed. Upstream and unmodified on both
    /// targets.
    pub loader_version: &'static str,
    /// The loader's own launcher metadata, immutable per loader version.
    ///
    /// This is the document Fabric Meta wraps and decorates. Taking it
    /// directly is what makes the install verifiable: it carries `sha1` and
    /// `size` inline for every library it names.
    pub document: PinnedFile,
    /// Libraries ash adds to the document's own, in classpath order.
    ///
    /// Always the intermediary and the loader jar, which the document does
    /// not name because they are not its dependencies - they are what it is.
    pub libraries: &'static [PinnedLibrary],
    /// JVM arguments the profile contributes.
    ///
    /// Per-pin because the two targets genuinely differ: Fabric adds one
    /// cosmetic argument, and Legacy Fabric's fork of the same service
    /// deliberately emits no argument block at all.
    pub jvm_arguments: &'static [&'static str],
    /// Third-party mods ash ships inside an instance, unmodified.
    ///
    /// Fetched into the depot and verified like any library - two instances
    /// on one version target share the bytes - and then copied into the
    /// instance's own `mods` directory, which is the only place a loader
    /// looks.
    pub bundled_mods: &'static [PinnedLibrary],
}

impl LoaderPin {
    /// Every artifact this pin mirrors, by its path in the depot.
    ///
    /// Planning asks the pin rather than reading a second URL out of the
    /// version document, because that document's type also parses metadata
    /// ash did not write - and a field there would let somebody else's
    /// document tell ash where to go. Here the answer can only come from
    /// ash's own source.
    pub(crate) fn mirrors(&self) -> Result<Vec<(String, &'static str)>, AshError> {
        let mut out = Vec::new();
        for library in self.libraries.iter().chain(self.bundled_mods) {
            if library.natives.is_empty() {
                if let Some(mirror) = library.mirror {
                    out.push((library.depot_path()?, mirror));
                }
                continue;
            }
            for native in library.natives {
                if let Some(mirror) = native.mirror {
                    out.push((library.native_depot_path(native.classifier)?, mirror));
                }
            }
        }
        Ok(out)
    }

    /// What the merged version document is called.
    ///
    /// The same shape both meta services use, so a depot stays legible to
    /// anyone who has installed Fabric the ordinary way.
    pub fn profile_id(&self, version_id: &str) -> String {
        format!("fabric-loader-{}-{version_id}", self.loader_version)
    }
}

// ---- the pins ---------------------------------------------------------------

const FABRIC_MAVEN: &str = "https://maven.fabricmc.net/";

/// Where ash mirrors the artifacts it cannot afford to lose.
///
/// A flat store rather than a repository layout, so a mirrored artifact
/// names its whole URL instead of deriving one. `+` becomes `-` in the file
/// names because GitHub mangles some characters in release asset names, and
/// a mirrored file that cannot be fetched under the name ash expects is a
/// mirror that does not work.
///
/// A macro rather than a `const` because the base has to be concatenated at
/// compile time, and `concat!` takes literals. Bumping the release means
/// editing this one line. See `docs/mirror.md`.
macro_rules! mirrored {
    ($file:literal) => {
        Some(concat!("https://github.com/sisi-j/ash/releases/download/mirror-2026-09-16/", $file))
    };
}

/// Fabric Loader 0.19.5 on 1.21.11.
///
/// Hashes read from the `.sha1` sidecars on 2026-09-15 and checked against a
/// local hash of the downloaded bytes. The document's own sidecar and a
/// locally computed hash agreed.
const FABRIC_1_21_11: LoaderPin = LoaderPin {
    loader: Loader::Fabric,
    version_id: "1.21.11",
    loader_version: "0.19.5",
    document: PinnedFile {
        url:
            "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.json",
        sha1: "4329bf31f0a437ad553c6d43368857ecb9bc6e71",
        size: 4257,
    },
    libraries: &[
        // The intermediary first, then the loader - the order Fabric Meta
        // itself writes them in, between the document's `common` libraries
        // and its `client` ones.
        PinnedLibrary {
            name: "net.fabricmc:intermediary:1.21.11",
            repository: FABRIC_MAVEN,
            mirror: None,
            sha1: "f1e2033afc8b637150c223b2bfd352d37544bc96",
            size: 797_685,
            natives: &[],
        },
        PinnedLibrary {
            name: "net.fabricmc:fabric-loader:0.19.5",
            repository: FABRIC_MAVEN,
            mirror: None,
            sha1: "ff9e65cffca4a67f31523e1807fe0855940fcbfa",
            size: 1_984_980,
            natives: &[],
        },
    ],
    // Cosmetic, and the only JVM argument the loader wants. It exists to
    // "emulate vanilla MC presence for programs that check the process
    // command line (discord, nvidia hybrid gpu, ..)", and the leading and
    // trailing spaces inside the value are Fabric's, not a typo.
    jvm_arguments: &["-DFabricMcEmu= net.minecraft.client.main.Main "],
    // Fabric API, Apache-2.0, shipped unmodified.
    bundled_mods: &[PinnedLibrary {
        name: "net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11",
        repository: FABRIC_MAVEN,
        // Not mirrored. `maven.fabricmc.net` is a well-resourced project
        // with fallback hosts of its own; what ash mirrors is Legacy
        // Fabric's single self-hosted server.
        mirror: None,
        sha1: "c98467cbbaf4d197377266795ae015f4130d65b6",
        size: 2_426_039,
        natives: &[],
    }],
};

const LEGACY_MAVEN: &str = "https://maven.legacyfabric.net/";

/// Fabric Loader 0.19.3 on 1.8.9, the Legacy Fabric way.
///
/// Every value here was read off `maven.legacyfabric.net` and
/// `meta.legacyfabric.net` on 2026-09-15, because the published sources
/// disagree with each other and none of them could be trusted alone - see
/// `docs/research/0002-fabric-loader-and-the-client-toolchain.md` gap 6.
///
/// Note the loader's group: `net.fabricmc`. Legacy Fabric's own meta service
/// serves upstream Fabric Loader unmodified, and 0.19.3 is the build it
/// pairs with 1.8.9.
const LEGACY_FABRIC_1_8_9: LoaderPin = LoaderPin {
    loader: Loader::LegacyFabric,
    version_id: "1.8.9",
    loader_version: "0.19.3",
    document: PinnedFile {
        url:
            "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.json",
        sha1: "a1bef7916f48a65491f0430743e51c1f4161054e",
        size: 4257,
    },
    libraries: &[
        PinnedLibrary {
            name: "net.legacyfabric:intermediary:1.8.9",
            repository: LEGACY_MAVEN,
            mirror: mirrored!("intermediary-1.8.9.jar"),
            sha1: "6622ac0b22cb62b24b6484e0fcec5436bf189161",
            size: 150_522,
            natives: &[],
        },
        PinnedLibrary {
            name: "net.fabricmc:fabric-loader:0.19.3",
            repository: FABRIC_MAVEN,
            // Upstream Fabric's Maven, not Legacy Fabric's, so not mirrored.
            mirror: None,
            sha1: "354dfaa02d0552e11867f85dff7cdbfaf813ba3e",
            size: 1_976_502,
            natives: &[],
        },
        // Legacy Fabric's LWJGL 2 fork, which replaces the game's own by
        // naming the same `group:artifact` at a different version. This is
        // not an optimisation: the fork exists to add arm64 natives, raise
        // the macOS floor, dispatch calls from the main thread and fix a
        // crash on Java 11+, and on Apple Silicon it is what makes 1.8.9 run
        // at all.
        PinnedLibrary {
            name: "org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17",
            repository: LEGACY_MAVEN,
            mirror: mirrored!("lwjgl-2.9.4-legacyfabric.17.jar"),
            sha1: "b0d3b134274c82aa401c90b42f0572063b1bf735",
            size: 1_081_524,
            natives: &[],
        },
        PinnedLibrary {
            name: "org.lwjgl.lwjgl:lwjgl_util:2.9.4+legacyfabric.17",
            repository: LEGACY_MAVEN,
            mirror: mirrored!("lwjgl_util-2.9.4-legacyfabric.17.jar"),
            sha1: "60b2bc63267f55fda5295d470e61d9fc672fa14c",
            size: 180_575,
            natives: &[],
        },
        PinnedLibrary {
            name: "org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17",
            repository: LEGACY_MAVEN,
            // The coordinate itself is not fetched, so it has no mirror -
            // the platform jars below carry their own.
            mirror: None,
            // No jar of its own - only the platform jars below, which is how
            // Mojang's 1.8.9 entry for the same coordinate is shaped.
            sha1: "",
            size: 0,
            natives: &[
                PinnedNative {
                    mirror: mirrored!("lwjgl-platform-2.9.4-legacyfabric.17-natives-windows.jar"),
                    os: Os::Windows,
                    classifier: "natives-windows",
                    sha1: "ef651faca2fc0fdf97ef53ed56bbe0881655a96e",
                    size: 2_491_132,
                },
                PinnedNative {
                    mirror: mirrored!("lwjgl-platform-2.9.4-legacyfabric.17-natives-osx.jar"),
                    os: Os::MacOs,
                    classifier: "natives-osx",
                    sha1: "720009ad4fb3ebc510d2452e97dec3b0c6b4182f",
                    size: 1_599_360,
                },
                // The fork publishes a Linux jar too. It is deliberately not
                // pinned: ash does not select natives for Linux, so it would
                // be four megabytes nothing could ever unpack.
            ],
        },
    ],
    // Deliberately empty. Legacy Fabric's fork of the meta service comments
    // the whole argument block out, `-DFabricMcEmu` included, above the note
    // "Prevent pre-1.13 from launching in vanilla launcher for some
    // reasons???". So a merged 1.8.9 profile has no structured arguments at
    // all and inherits vanilla's `minecraftArguments` instead.
    jvm_arguments: &[],
    // Legacy Fabric API 1.13.5+1.8.9, Apache-2.0, shipped unmodified.
    //
    // Unlike Fabric API this is *not* a fat jar: it is a metadata-only
    // aggregator - four entries, no classes - and its POM names 43 separate
    // module artifacts. Which of those ash's client needs cannot be known
    // until the client exists, so only the aggregator is pinned here and the
    // modules are #21's to add.
    bundled_mods: &[PinnedLibrary {
        name: "net.legacyfabric.legacy-fabric-api:legacy-fabric-api:1.13.5+1.8.9",
        repository: LEGACY_MAVEN,
        mirror: mirrored!("legacy-fabric-api-1.13.5-1.8.9.jar"),
        sha1: "4cc125464f3894bdad83eb5ad82c4ae0f290b344",
        size: 5_217,
        natives: &[],
    }],
};

/// Every loader ash ships a pin for.
///
/// Carried on [`crate::Config`] rather than reached for directly, for the
/// same reason the depot and instance roots are: they are ash's own values,
/// and a test that could not point them somewhere else would have to serve
/// several megabytes of real third-party jars to exercise a modded prepare.
pub const PINS: &[LoaderPin] = &[FABRIC_1_21_11, LEGACY_FABRIC_1_8_9];

/// The loader ash installs for a version target, if it has one pinned.
///
/// `None` is a real answer and the only honest one: ash pins what it has
/// tested, and a loader it has not pinned for a version target is an instance
/// that could never launch. This is what stops one being created.
pub(crate) fn pin_for(
    pins: &'static [LoaderPin],
    loader: Loader,
    version_id: &str,
) -> Option<&'static LoaderPin> {
    if loader == Loader::Vanilla {
        // Vanilla is a loader, but it is the one with nothing to install.
        return None;
    }
    pins.iter().find(|pin| pin.loader == loader && pin.version_id == version_id)
}

/// The loaders a version target can actually run, vanilla always among them.
///
/// Read off the pins rather than duplicated in the UI, so a loader ash cannot
/// install is a loader the player is never offered.
pub(crate) fn loaders_for(pins: &'static [LoaderPin], version_id: &str) -> Vec<Loader> {
    Loader::ALL.into_iter().filter(|&loader| is_supported(pins, loader, version_id)).collect()
}

/// Whether ash can build this pairing at all.
pub(crate) fn is_supported(pins: &'static [LoaderPin], loader: Loader, version_id: &str) -> bool {
    loader == Loader::Vanilla || pin_for(pins, loader, version_id).is_some()
}

// ---- the loader's own metadata ----------------------------------------------

/// The shape of `fabric-loader-<version>.json`.
///
/// Only what ash needs is modelled. `libraries.development` and
/// `libraries.server` are deliberately absent rather than parsed and
/// discarded: serde ignores what it is not asked for, and the development
/// entry - MixinExtras - belongs in a Loom workspace, not on a player's
/// classpath. Fabric Meta leaves both out of a client profile too.
#[derive(Debug, Deserialize)]
struct LoaderDocument {
    #[serde(default)]
    libraries: DocumentLibraries,
    #[serde(rename = "mainClass")]
    main_class: DocumentMainClass,
}

#[derive(Debug, Default, Deserialize)]
struct DocumentLibraries {
    #[serde(default)]
    common: Vec<DocumentLibrary>,
    #[serde(default)]
    client: Vec<DocumentLibrary>,
}

/// A library the loader names, with its hash and size already inline.
///
/// This is why the pinned document is worth taking directly: ash gets a
/// verifiable entry for ASM and Mixin without asking anything else for it.
#[derive(Debug, Deserialize)]
struct DocumentLibrary {
    name: String,
    url: String,
    sha1: String,
    size: u64,
}

/// `mainClass` is an object, not a string, on version 2 of this document.
///
/// Fabric Meta reads `.client` out of it when building a client profile and
/// writes the string form; ash does the same rather than passing the object
/// through, because the merged document has to look like Mojang's.
#[derive(Debug, Deserialize)]
struct DocumentMainClass {
    client: String,
}

// ---- the profile ash writes -------------------------------------------------

/// The child document ash synthesises, in Mojang's own shape.
///
/// Fabric Meta emits seven fields and leaves its own two library entries
/// bare, as `{name, url}` with no hash, because it has nothing more to say
/// about them. ash writes a full `downloads.artifact` for every entry
/// instead - it has the hashes, inline from the document or pinned here -
/// which is what lets the depot plan, verify and share a modded classpath
/// with no special case for where a library came from.
#[derive(Debug, Serialize)]
struct Profile {
    id: String,
    #[serde(rename = "inheritsFrom")]
    inherits_from: String,
    // Deliberately no `type`. Fabric Meta writes "release" unconditionally,
    // which the merge would let win and so relabel a snapshot version target
    // as a release. Leaving it out lets the parent say what it is.
    #[serde(rename = "mainClass")]
    main_class: String,
    arguments: ProfileArguments,
    libraries: Vec<ProfileLibrary>,
}

#[derive(Debug, Serialize)]
struct ProfileArguments {
    /// Always empty, and present on purpose. Fabric's own source says an
    /// absent game-argument list makes the official launcher complain.
    game: Vec<String>,
    jvm: Vec<String>,
}

#[derive(Debug, Serialize)]
struct ProfileLibrary {
    name: String,
    /// Which classifier carries each platform's native jar.
    ///
    /// Its presence is what says "unpack this" - preparation does not check
    /// a version anywhere, and must not start.
    #[serde(skip_serializing_if = "Option::is_none")]
    natives: Option<BTreeMap<String, String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    extract: Option<ProfileExtract>,
    downloads: ProfileDownloads,
}

#[derive(Debug, Serialize)]
struct ProfileExtract {
    exclude: Vec<String>,
}

#[derive(Debug, Serialize)]
struct ProfileDownloads {
    /// Absent on a library whose coordinate names no single jar.
    #[serde(skip_serializing_if = "Option::is_none")]
    artifact: Option<ProfileArtifact>,
    #[serde(skip_serializing_if = "BTreeMap::is_empty")]
    classifiers: BTreeMap<String, ProfileArtifact>,
}

#[derive(Debug, Serialize)]
struct ProfileArtifact {
    path: String,
    sha1: String,
    size: u64,
    url: String,
}

/// Where a Maven coordinate's jar lives, relative to a repository root.
///
/// `group:artifact:version` with an optional `:classifier`. Fabric's own
/// installer builds exactly this path, though its `split(":", 3)` cannot
/// handle a classifier at all - which is why this splits fully and ash can
/// pin a native jar where Fabric's installer could not name one.
fn maven_path(coordinate: &str) -> Option<String> {
    let mut parts = coordinate.split(':');
    let group = parts.next()?.replace('.', "/");
    let artifact = parts.next()?;
    let version = parts.next()?;
    if group.is_empty() || artifact.is_empty() || version.is_empty() {
        return None;
    }
    let classifier = match parts.next() {
        Some(classifier) if !classifier.is_empty() => format!("-{classifier}"),
        _ => String::new(),
    };
    Some(format!("{group}/{artifact}/{version}/{artifact}-{version}{classifier}.jar"))
}

/// A coordinate ash pinned but cannot turn into a path is a bug in the pin,
/// not something a player can cause or fix.
fn coordinate_error(coordinate: &str) -> AshError {
    AshError::Malformed {
        url: coordinate.to_owned(),
        detail: "a pinned loader coordinate is not a Maven coordinate".into(),
    }
}

impl PinnedLibrary {
    /// Where this artifact lives, relative to the depot root.
    pub(crate) fn depot_path(&self) -> Result<String, AshError> {
        maven_path(self.name)
            .map(|path| format!("libraries/{path}"))
            .ok_or_else(|| coordinate_error(self.name))
    }

    /// The full URL to fetch it from.
    pub(crate) fn url(&self) -> Result<String, AshError> {
        let path = maven_path(self.name).ok_or_else(|| coordinate_error(self.name))?;
        Ok(format!("{}{path}", self.repository))
    }

    /// The jar's own file name, for a mod that has to land in a game
    /// directory under a name the loader will recognise.
    pub(crate) fn file_name(&self) -> Result<String, AshError> {
        let path = maven_path(self.name).ok_or_else(|| coordinate_error(self.name))?;
        path.rsplit('/').next().map(str::to_owned).ok_or_else(|| coordinate_error(self.name))
    }

    /// Where one of this library's platform jars lives, relative to the
    /// depot root.
    pub(crate) fn native_depot_path(&self, classifier: &str) -> Result<String, AshError> {
        let coordinate = format!("{}:{}", self.name, classifier);
        maven_path(&coordinate)
            .map(|path| format!("libraries/{path}"))
            .ok_or_else(|| coordinate_error(self.name))
    }

    /// The artifact id alone, which every version of this jar shares.
    ///
    /// What identifies one bundled mod across a change of pin, so installing
    /// a new version can take the old one with it.
    pub(crate) fn artifact(&self) -> Result<&'static str, AshError> {
        self.name.split(':').nth(1).ok_or_else(|| coordinate_error(self.name))
    }
}

/// Build the version document for a loader on a version target.
///
/// `document` is the pinned loader metadata, already verified against the
/// hash in the pin. The result is a child document that inherits from the
/// vanilla version target, which is exactly what a Fabric install writes
/// into `versions/` the ordinary way - so the depot stays legible, and the
/// merge that resolves it is the same one any launcher performs.
pub(crate) fn synthesise_profile(
    pin: &LoaderPin,
    version_id: &str,
    document: &[u8],
) -> Result<Vec<u8>, AshError> {
    let parsed: LoaderDocument = serde_json::from_slice(document).map_err(|e| {
        AshError::Malformed { url: pin.document.url.to_owned(), detail: e.to_string() }
    })?;

    let mut libraries: Vec<ProfileLibrary> = Vec::new();

    // The document's shared libraries first, then ash's pinned entries -
    // the intermediary and the loader - then the document's client-only
    // ones. That is the order Fabric Meta writes, and classpath order is
    // the one thing about a modded launch that is not forgiving.
    for library in &parsed.libraries.common {
        libraries.push(profile_library(&library.name, &library.url, &library.sha1, library.size)?);
    }
    for library in pin.libraries {
        libraries.push(pinned_library(library)?);
    }
    for library in &parsed.libraries.client {
        libraries.push(profile_library(&library.name, &library.url, &library.sha1, library.size)?);
    }

    let profile = Profile {
        id: pin.profile_id(version_id),
        inherits_from: version_id.to_owned(),
        main_class: parsed.main_class.client,
        arguments: ProfileArguments {
            game: Vec::new(),
            jvm: pin.jvm_arguments.iter().map(|a| (*a).to_owned()).collect(),
        },
        libraries,
    };

    serde_json::to_vec_pretty(&profile)
        .map_err(|e| AshError::Storage { detail: format!("encoding a loader profile: {e}") })
}

/// One library entry, whichever half of the pin it came from.
///
/// The two sources differ only in where the hash was written down - inline
/// in the loader's own document, or in ash's pin for the entries that
/// document does not name - and not at all in what ash writes out.
fn profile_library(
    name: &str,
    repository: &str,
    sha1: &str,
    size: u64,
) -> Result<ProfileLibrary, AshError> {
    let path = maven_path(name).ok_or_else(|| coordinate_error(name))?;
    Ok(ProfileLibrary {
        name: name.to_owned(),
        natives: None,
        extract: None,
        downloads: ProfileDownloads {
            artifact: Some(ProfileArtifact {
                url: format!("{repository}{path}"),
                path,
                sha1: sha1.to_owned(),
                size,
            }),
            classifiers: BTreeMap::new(),
        },
    })
}

/// One entry for a library ash pinned, unpacked or not.
fn pinned_library(library: &PinnedLibrary) -> Result<ProfileLibrary, AshError> {
    let mut entry = profile_library(library.name, library.repository, library.sha1, library.size)?;
    if library.natives.is_empty() {
        return Ok(entry);
    }

    // The coordinate names no jar of its own, so the artifact that
    // `profile_library` built for it would be a path nothing serves.
    entry.downloads.artifact = None;

    for native in library.natives {
        let coordinate = format!("{}:{}", library.name, native.classifier);
        let path = maven_path(&coordinate).ok_or_else(|| coordinate_error(library.name))?;
        entry
            .natives
            .get_or_insert_with(BTreeMap::new)
            .insert(native.os.natives_key().to_owned(), native.classifier.to_owned());
        entry.downloads.classifiers.insert(
            native.classifier.to_owned(),
            ProfileArtifact {
                url: format!("{}{path}", library.repository),
                path,
                sha1: native.sha1.to_owned(),
                size: native.size,
            },
        );
    }

    // What both Mojang and Legacy Fabric exclude, and for the same reason:
    // the signature and manifest in a native jar are not native libraries,
    // and unpacking them next to the real ones is how a signed jar becomes
    // one the JVM refuses.
    entry.extract = Some(ProfileExtract { exclude: vec!["META-INF/".to_owned()] });
    Ok(entry)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::version::VersionMetadata;

    /// The real 1.21.11 document, cut to two libraries. The shape is what
    /// matters: `mainClass` is an object, and every entry carries its own
    /// hash and size.
    const DOCUMENT: &str = r#"{
        "version": 2,
        "min_java_version": 8,
        "libraries": {
            "client": [],
            "common": [
                {"name":"org.ow2.asm:asm:9.10.1","url":"https://maven.fabricmc.net/",
                 "sha1":"ada2141c0cc5","size":126151},
                {"name":"net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7",
                 "url":"https://maven.fabricmc.net/","sha1":"5f66cc9f59b8","size":1539080}
            ],
            "development": [
                {"name":"io.github.llamalad7:mixinextras-fabric:0.5.5",
                 "url":"https://maven.fabricmc.net/","sha1":"d1055b99c0ab","size":727864}
            ]
        },
        "mainClass": {
            "client": "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "server": "net.fabricmc.loader.impl.launch.knot.KnotServer"
        }
    }"#;

    fn synthesised() -> VersionMetadata {
        let bytes = synthesise_profile(&FABRIC_1_21_11, "1.21.11", DOCUMENT.as_bytes())
            .expect("the pinned document shape");
        serde_json::from_slice(&bytes).expect("the profile parses as version metadata")
    }

    #[test]
    fn a_maven_coordinate_becomes_a_repository_path() {
        assert_eq!(
            maven_path("net.fabricmc:fabric-loader:0.19.5").as_deref(),
            Some("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar")
        );
        // A classifier, which Fabric's own installer cannot express.
        assert_eq!(
            maven_path("org.lwjgl.lwjgl:lwjgl-platform:2.9.4:natives-windows").as_deref(),
            Some("org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-windows.jar")
        );
        // A version with a `+` in it is ordinary here and must not be
        // escaped or split - both loaders use them.
        assert_eq!(
            maven_path("net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11").as_deref(),
            Some(
                "net/fabricmc/fabric-api/fabric-api/0.141.6+1.21.11/fabric-api-0.141.6+1.21.11.jar"
            )
        );
        assert_eq!(maven_path("not-a-coordinate"), None);
    }

    #[test]
    fn the_profile_inherits_from_the_version_target() {
        let profile = synthesised();

        assert_eq!(profile.id, "fabric-loader-0.19.5-1.21.11");
        assert_eq!(profile.inherits_from.as_deref(), Some("1.21.11"));
    }

    #[test]
    fn the_main_class_is_read_out_of_the_object_form() {
        // The document says `{"client": ..., "server": ...}`; a merged
        // document has to carry Mojang's string form or launching cannot
        // read it at all.
        assert_eq!(
            synthesised().main_class.as_deref(),
            Some("net.fabricmc.loader.impl.launch.knot.KnotClient")
        );
    }

    #[test]
    fn every_library_carries_a_hash_and_a_reachable_url() {
        let profile = synthesised();

        assert!(!profile.libraries.is_empty(), "no libraries means nothing was proved");
        for library in &profile.libraries {
            let artifact = library
                .downloads
                .artifact
                .as_ref()
                .unwrap_or_else(|| panic!("{} has no artifact", library.name));
            assert!(!artifact.sha1.is_empty(), "{} has no hash", library.name);
            assert!(artifact.size > 0, "{} has no size", library.name);
            assert!(
                artifact.url.starts_with("https://"),
                "{} has no url: {}",
                library.name,
                artifact.url
            );
            assert!(artifact.path.is_some(), "{} has no depot path", library.name);
        }
    }

    #[test]
    fn the_loader_and_the_intermediary_sit_between_the_documents_own_libraries() {
        let names: Vec<String> =
            synthesised().libraries.into_iter().map(|library| library.name).collect();

        assert_eq!(
            names,
            [
                "org.ow2.asm:asm:9.10.1",
                "net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7",
                "net.fabricmc:intermediary:1.21.11",
                "net.fabricmc:fabric-loader:0.19.5",
            ]
        );
    }

    #[test]
    fn the_development_libraries_are_left_out() {
        // MixinExtras belongs in a Loom workspace. Fabric Meta leaves it out
        // of a client profile, and shipping it would put a library on a
        // player's classpath that nothing there loads.
        let names: Vec<String> =
            synthesised().libraries.into_iter().map(|library| library.name).collect();

        assert!(!names.is_empty(), "the test proves nothing if no libraries were built");
        assert!(
            !names.iter().any(|name| name.contains("mixinextras")),
            "a development-only library reached the profile: {names:?}"
        );
    }

    #[test]
    fn the_only_jvm_argument_is_the_one_fabric_asks_for() {
        let profile = synthesised();

        assert_eq!(
            crate::version::resolve_arguments(&profile.arguments.jvm, crate::version::Os::Windows),
            ["-DFabricMcEmu= net.minecraft.client.main.Main "]
        );
        // Present and empty, not absent: Fabric's own source says an absent
        // game list makes the official launcher complain.
        assert!(profile.arguments.game.is_empty());
    }

    #[test]
    fn each_version_target_offers_only_the_loaders_ash_pinned_for_it() {
        assert_eq!(loaders_for(PINS, "1.21.11"), [Loader::Vanilla, Loader::Fabric]);
        assert_eq!(loaders_for(PINS, "1.8.9"), [Loader::Vanilla, Loader::LegacyFabric]);
        // A version target ash has tested nothing on runs vanilla only, so
        // no instance can be created that could never launch.
        assert_eq!(loaders_for(PINS, "1.16.5"), [Loader::Vanilla]);

        assert!(is_supported(PINS, Loader::Vanilla, "1.16.5"), "vanilla runs on anything");
        assert!(!is_supported(PINS, Loader::Fabric, "1.16.5"));
    }

    #[test]
    fn every_pinned_library_names_either_a_jar_or_its_platform_jars() {
        // The two are alternatives, and which one a library is comes from
        // `natives` being empty rather than from the hash being blank. A pin
        // that said neither would put an artifact with no hash into a plan,
        // and the depot's whole invariant is that there is no such thing.
        let libraries = PINS.iter().flat_map(|pin| pin.libraries.iter().chain(pin.bundled_mods));
        let mut checked = 0;
        for library in libraries {
            checked += 1;
            if library.natives.is_empty() {
                assert!(
                    !library.sha1.is_empty(),
                    "{} has neither a hash nor natives",
                    library.name
                );
                assert!(library.size > 0, "{} has no size", library.name);
            } else {
                for native in library.natives {
                    assert!(!native.sha1.is_empty(), "{} has a native with no hash", library.name);
                    assert!(native.size > 0, "{} has a native with no size", library.name);
                }
            }
        }
        assert!(checked > 0, "the test proves nothing if ash pins no libraries");
    }

    #[test]
    fn every_pin_names_the_loader_and_target_it_is_found_by() {
        assert!(!PINS.is_empty(), "the test proves nothing if ash pins no loaders");
        // The table is searched by these two fields, so a pin whose own
        // fields disagree with where it sits would simply never be found.
        for pin in PINS {
            assert_eq!(pin_for(PINS, pin.loader, pin.version_id), Some(pin));
            assert!(!pin.loader_version.is_empty());
            assert!(pin.document.size > 0, "{} has no document size", pin.version_id);
        }
    }
}
