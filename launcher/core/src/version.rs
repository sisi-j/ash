//! Mojang's per-version metadata: what a version target is made of.
//!
//! Only the parts preparation needs are modelled here. Arguments, the main
//! class and the classpath ordering that launching cares about land in #8;
//! the rule evaluation below is shared with them, which is why it is written
//! generally rather than only for libraries.

use serde::Deserialize;

use crate::error::AshError;

/// The platforms ash targets. Linux is deliberately absent - it is out of
/// scope for v1, and pretending otherwise would mean shipping untested
/// native-library selection.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Os {
    Windows,
    MacOs,
}

impl Os {
    /// What Mojang calls this platform in a rule.
    pub fn mojang_name(self) -> &'static str {
        match self {
            Os::Windows => "windows",
            Os::MacOs => "osx",
        }
    }

    /// The `natives` classifier key for this platform.
    pub fn natives_key(self) -> &'static str {
        match self {
            Os::Windows => "windows",
            Os::MacOs => "osx",
        }
    }

    /// Maven classifiers that carry this platform's native libraries, best
    /// first.
    ///
    /// Rules cannot answer this. Mojang tags `natives-windows`,
    /// `natives-windows-arm64` and `natives-windows-x86` with the *same*
    /// rule - `{"os": {"name": "windows"}}` - so rule evaluation keeps all
    /// three. The architecture lives only in the classifier.
    ///
    /// More than one entry means a fallback: an arm64 machine takes the
    /// arm64 jar when the version publishes one and the x64 jar otherwise,
    /// because emulated natives beat no natives.
    pub fn natives_classifiers(self) -> &'static [&'static str] {
        match self {
            Os::Windows => {
                if cfg!(target_arch = "aarch64") {
                    &["natives-windows-arm64", "natives-windows"]
                } else {
                    &["natives-windows"]
                }
            }
            Os::MacOs => {
                if cfg!(target_arch = "aarch64") {
                    &["natives-macos-arm64", "natives-macos", "natives-osx"]
                } else {
                    &["natives-macos", "natives-osx"]
                }
            }
        }
    }

    pub fn current() -> Os {
        if cfg!(target_os = "macos") {
            Os::MacOs
        } else {
            Os::Windows
        }
    }
}

// ---- wire format -----------------------------------------------------------

#[derive(Debug, Clone, Deserialize)]
pub struct VersionMetadata {
    pub id: String,
    #[serde(rename = "mainClass")]
    pub main_class: Option<String>,
    #[serde(rename = "assetIndex")]
    pub asset_index: Option<AssetIndexRef>,
    #[serde(default)]
    pub downloads: Downloads,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(rename = "javaVersion")]
    pub java_version: Option<JavaVersion>,
    /// The structured argument lists, 1.13 and later.
    #[serde(default)]
    pub arguments: Arguments,
    /// The pre-1.13 single string. Kept so assembly has one shape to work
    /// with; making 1.8.9 actually run is #9.
    #[serde(rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
    /// `release`, `snapshot`, ... Passed through as `${version_type}`.
    #[serde(rename = "type")]
    pub version_type: Option<String>,
}

/// The 1.13-and-later argument lists.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<ArgEntry>,
    #[serde(default)]
    pub jvm: Vec<ArgEntry>,
}

/// One entry in an argument list: either a bare string, or a value guarded
/// by rules.
#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum ArgEntry {
    Literal(String),
    Conditional { rules: Vec<Rule>, value: ArgValue },
}

/// Mojang writes a guarded value as either one string or a list of them.
#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum ArgValue {
    One(String),
    Many(Vec<String>),
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct Downloads {
    pub client: Option<DownloadRef>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DownloadRef {
    pub sha1: String,
    pub size: u64,
    pub url: String,
    /// Present on library artifacts, absent on the client jar.
    #[serde(default)]
    pub path: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct JavaVersion {
    pub component: String,
    /// Read by launching (#8) to sanity-check the runtime it was handed.
    #[allow(dead_code)]
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

/// `name` is the Maven coordinate. Launching (#8) uses it to order and
/// deduplicate the classpath.
#[allow(dead_code)]
#[derive(Debug, Clone, Deserialize)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: LibraryDownloads,
    #[serde(default)]
    pub rules: Vec<Rule>,
    /// 1.8.9-era: maps an OS to a key in `downloads.classifiers`.
    #[serde(default)]
    pub natives: std::collections::HashMap<String, String>,
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct LibraryDownloads {
    pub artifact: Option<DownloadRef>,
    /// 1.8.9-era native jars live here rather than in `artifact`.
    #[serde(default)]
    pub classifiers: std::collections::HashMap<String, DownloadRef>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Rule {
    pub action: String,
    pub os: Option<OsRule>,
    /// Optional launcher features the rule requires, e.g. `is_demo_user`.
    ///
    /// ash turns none of these on. Parsing the clause is what makes that
    /// true: serde would otherwise drop the field, leaving a rule that reads
    /// as an unconditional `allow` and putting `--demo` on every launch.
    #[serde(default)]
    pub features: std::collections::HashMap<String, bool>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub arch: Option<String>,
}

// ---- rule evaluation -------------------------------------------------------

impl Rule {
    fn matches(&self, os: Os) -> bool {
        // Every feature ash could be asked for is off, so a rule that needs
        // one can never match - whichever way its action points.
        if !self.features.is_empty() {
            return false;
        }
        let Some(rule) = &self.os else {
            // A rule with no `os` clause matches everything.
            return true;
        };
        if let Some(name) = &rule.name {
            if name != os.mojang_name() {
                return false;
            }
        }
        if let Some(arch) = &rule.arch {
            // ash targets 64-bit only; the 32-bit rules in old manifests must
            // not match, or we would download x86 natives onto x64.
            if arch != "x64" && arch != "x86_64" {
                return false;
            }
        }
        true
    }
}

/// Mojang's rule semantics: start disallowed unless there are no rules at
/// all, then let each matching rule overwrite the verdict in order.
pub fn rules_allow(rules: &[Rule], os: Os) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        if rule.matches(os) {
            allowed = rule.action == "allow";
        }
    }
    allowed
}

impl Library {
    /// `group:artifact:version`, with any classifier dropped.
    pub fn coordinate(&self) -> &str {
        match self.name.match_indices(':').nth(2) {
            Some((at, _)) => &self.name[..at],
            None => &self.name,
        }
    }

    /// The `natives-*` classifier from the Maven coordinate, if this is a
    /// native jar at all.
    pub fn native_classifier(&self) -> Option<&str> {
        self.name.split(':').nth(3).filter(|c| c.starts_with("natives-"))
    }

    // There is deliberately no `artifact_for(os)` here. Rules alone are not
    // enough to pick a library - see `select_libraries`, which is the only
    // correct way to ask - and a method that looked like it answered the
    // question would quietly reintroduce the three-native-jars bug.

    /// The native jar to extract, for versions that still use classifiers.
    pub fn natives_for(&self, os: Os) -> Option<&DownloadRef> {
        if !rules_allow(&self.rules, os) {
            return None;
        }
        let classifier = self.natives.get(os.natives_key())?;
        // Mojang templates `${arch}` into some 1.8.9-era classifiers.
        let classifier = classifier.replace("${arch}", "64");
        self.downloads.classifiers.get(&classifier)
    }
}

/// The libraries that belong on this machine, in metadata order.
///
/// Rules decide most of it, but they cannot decide natives: a 1.21.x
/// manifest tags `natives-windows`, `natives-windows-arm64` and
/// `natives-windows-x86` with one identical `{"os": {"name": "windows"}}`
/// rule, so rule evaluation alone keeps all three. That would download three
/// jars to use one and put 32-bit natives on a 64-bit classpath.
///
/// So native jars are grouped by coordinate and exactly one is taken per
/// group, chosen by classifier.
pub fn select_libraries(libraries: &[Library], os: Os) -> Vec<&Library> {
    let permitted: Vec<&Library> =
        libraries.iter().filter(|library| rules_allow(&library.rules, os)).collect();

    let mut chosen: Vec<&Library> = Vec::new();
    let mut resolved: Vec<&str> = Vec::new();

    for &library in &permitted {
        if library.native_classifier().is_none() {
            chosen.push(library);
            continue;
        }

        // One native jar per coordinate, at the position of its first
        // candidate, so classpath order still follows the metadata.
        let coordinate = library.coordinate();
        if resolved.contains(&coordinate) {
            continue;
        }
        resolved.push(coordinate);

        for wanted in os.natives_classifiers() {
            let found = permitted.iter().find(|candidate| {
                candidate.coordinate() == coordinate
                    && candidate.native_classifier() == Some(*wanted)
            });
            if let Some(&best) = found {
                chosen.push(best);
                break;
            }
        }
    }

    chosen
}

/// Flatten an argument list, keeping only what the rules permit here.
pub fn resolve_arguments(entries: &[ArgEntry], os: Os) -> Vec<String> {
    let mut out = Vec::new();
    for entry in entries {
        match entry {
            ArgEntry::Literal(value) => out.push(value.clone()),
            ArgEntry::Conditional { rules, value } => {
                if rules_allow(rules, os) {
                    match value {
                        ArgValue::One(one) => out.push(one.clone()),
                        ArgValue::Many(many) => out.extend(many.iter().cloned()),
                    }
                }
            }
        }
    }
    out
}

pub fn parse(url: &str, body: &[u8]) -> Result<VersionMetadata, AshError> {
    serde_json::from_slice(body)
        .map_err(|e| AshError::Malformed { url: url.to_owned(), detail: e.to_string() })
}

// ---- asset index -----------------------------------------------------------

#[derive(Debug, Clone, Deserialize)]
pub struct AssetIndex {
    pub objects: std::collections::HashMap<String, AssetObject>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

/// Mojang serves assets content-addressed by SHA-1, two-character prefix
/// directory first.
pub fn asset_url(hash: &str) -> String {
    format!("https://resources.download.minecraft.net/{}/{}", &hash[..2], hash)
}

pub fn asset_path(hash: &str) -> String {
    format!("assets/objects/{}/{}", &hash[..2], hash)
}

pub fn parse_asset_index(url: &str, body: &[u8]) -> Result<AssetIndex, AshError> {
    serde_json::from_slice(body)
        .map_err(|e| AshError::Malformed { url: url.to_owned(), detail: e.to_string() })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rule(action: &str, os: Option<&str>) -> Rule {
        Rule {
            action: action.to_owned(),
            os: os.map(|name| OsRule { name: Some(name.to_owned()), arch: None }),
            features: Default::default(),
        }
    }

    fn library(name: &str, rules: Vec<Rule>) -> Library {
        Library {
            name: name.to_owned(),
            downloads: Default::default(),
            rules,
            natives: Default::default(),
        }
    }

    #[test]
    fn no_rules_means_allowed() {
        assert!(rules_allow(&[], Os::Windows));
    }

    #[test]
    fn a_bare_allow_permits_every_platform() {
        let rules = vec![rule("allow", None)];
        assert!(rules_allow(&rules, Os::Windows));
        assert!(rules_allow(&rules, Os::MacOs));
    }

    #[test]
    fn a_later_disallow_overrides_an_earlier_allow() {
        // The shape Mojang actually ships: allow everywhere, then carve out.
        let rules = vec![rule("allow", None), rule("disallow", Some("osx"))];
        assert!(rules_allow(&rules, Os::Windows));
        assert!(!rules_allow(&rules, Os::MacOs));
    }

    #[test]
    fn an_allow_scoped_to_one_os_excludes_the_others() {
        let rules = vec![rule("allow", Some("osx"))];
        assert!(rules_allow(&rules, Os::MacOs));
        assert!(!rules_allow(&rules, Os::Windows));
    }

    #[test]
    fn a_32_bit_arch_rule_never_matches() {
        // Old manifests carry x86 entries; matching one would put 32-bit
        // natives on a 64-bit classpath.
        let rules = vec![Rule {
            action: "allow".into(),
            os: Some(OsRule { name: Some("windows".into()), arch: Some("x86".into()) }),
            features: Default::default(),
        }];
        assert!(!rules_allow(&rules, Os::Windows));
    }

    #[test]
    fn a_rule_that_asks_for_a_feature_never_matches() {
        // Every conditional game argument in 1.21.x is feature-gated. ash
        // turns none of them on, so treating an unparsed `features` clause
        // as an unconditional allow would launch the game in demo mode with
        // a literal `${resolution_width}` on the command line.
        let mut features = std::collections::HashMap::new();
        features.insert("is_demo_user".to_owned(), true);
        let rules = vec![Rule { action: "allow".into(), os: None, features }];
        assert!(!rules_allow(&rules, Os::Windows));
    }

    #[test]
    fn feature_gated_arguments_are_dropped_whole() {
        let entries: Vec<ArgEntry> = serde_json::from_str(
            r#"["--username", "${auth_player_name}",
                {"rules":[{"action":"allow","features":{"is_demo_user":true}}],
                 "value":"--demo"},
                {"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],
                 "value":["--width","${resolution_width}"]}]"#,
        )
        .expect("the real 1.21.x game argument shape");

        assert_eq!(resolve_arguments(&entries, Os::Windows), ["--username", "${auth_player_name}"]);
    }

    #[test]
    fn a_guarded_value_may_be_one_string_or_a_list() {
        let entries: Vec<ArgEntry> = serde_json::from_str(
            r#"[{"rules":[{"action":"allow","os":{"name":"windows"}}],"value":"-XX:Flag"},
                {"rules":[{"action":"allow","os":{"name":"windows"}}],"value":["-a","-b"]}]"#,
        )
        .expect("Mojang writes both shapes");

        assert_eq!(resolve_arguments(&entries, Os::Windows), ["-XX:Flag", "-a", "-b"]);
        assert!(resolve_arguments(&entries, Os::MacOs).is_empty());
    }

    #[test]
    fn one_native_jar_is_taken_per_coordinate() {
        // All three carry the same rule - only the classifier separates
        // them - so rules alone would keep every one.
        let windows = vec![rule("allow", Some("windows"))];
        let libraries = vec![
            library("org.lwjgl:lwjgl:3.3.3", vec![]),
            library("org.lwjgl:lwjgl:3.3.3:natives-windows", windows.clone()),
            library("org.lwjgl:lwjgl:3.3.3:natives-windows-x86", windows.clone()),
            library("org.lwjgl:lwjgl:3.3.3:natives-windows-arm64", windows),
            library("org.lwjgl:lwjgl:3.3.3:natives-macos", vec![rule("allow", Some("osx"))]),
        ];

        let chosen: Vec<&str> =
            select_libraries(&libraries, Os::Windows).iter().map(|l| l.name.as_str()).collect();

        let expected = if cfg!(target_arch = "aarch64") {
            "org.lwjgl:lwjgl:3.3.3:natives-windows-arm64"
        } else {
            "org.lwjgl:lwjgl:3.3.3:natives-windows"
        };
        assert_eq!(chosen, ["org.lwjgl:lwjgl:3.3.3", expected]);
    }

    #[test]
    fn a_platform_without_its_own_native_jar_falls_back() {
        // Versions before the Apple Silicon split publish only
        // `natives-macos`. Taking nothing would leave the game with no
        // natives at all; the x64 jar under emulation is the better answer.
        let libraries = vec![library(
            "org.lwjgl:lwjgl:3.2.2:natives-macos",
            vec![rule("allow", Some("osx"))],
        )];

        let chosen = select_libraries(&libraries, Os::MacOs);

        assert_eq!(chosen.len(), 1);
        assert_eq!(chosen[0].native_classifier(), Some("natives-macos"));
    }

    #[test]
    fn a_coordinate_drops_its_classifier() {
        let native = library("org.lwjgl:lwjgl:3.3.3:natives-windows", vec![]);
        assert_eq!(native.coordinate(), "org.lwjgl:lwjgl:3.3.3");
        assert_eq!(native.native_classifier(), Some("natives-windows"));

        // A classifier that is not a natives classifier is left alone.
        let sources = library("com.example:thing:1.0:sources", vec![]);
        assert_eq!(sources.native_classifier(), None);
    }
}
