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

    pub fn current() -> Os {
        if cfg!(target_os = "macos") {
            Os::MacOs
        } else {
            Os::Windows
        }
    }
}

// ---- wire format -----------------------------------------------------------

/// `main_class` is read by launching (#8). It is part of the wire shape and
/// parsed here so the format lives in one place.
#[allow(dead_code)]
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
}

#[derive(Debug, Clone, Deserialize)]
pub struct OsRule {
    pub name: Option<String>,
    pub arch: Option<String>,
}

// ---- rule evaluation -------------------------------------------------------

impl Rule {
    fn matches(&self, os: Os) -> bool {
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
    /// The jar that belongs on the classpath, if this library applies here.
    pub fn artifact_for(&self, os: Os) -> Option<&DownloadRef> {
        if !rules_allow(&self.rules, os) {
            return None;
        }
        self.downloads.artifact.as_ref()
    }

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
        }];
        assert!(!rules_allow(&rules, Os::Windows));
    }
}
