//! Turning a prepared instance into a command line.
//!
//! Everything here is a pure function of the version metadata, the depot
//! layout and the session. Nothing spawns, nothing touches the network, and
//! the result is inspectable before it runs - which is the whole reason the
//! assembly is separate from the process port.

use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};

use crate::error::AshError;
use crate::process::Invocation;
use crate::runtime::Runtime;
use crate::version::{self, Os, VersionMetadata};

/// What ash calls itself to the game. Mojang's own metadata asks for this
/// via `${launcher_name}`, and it ends up in crash reports.
const LAUNCHER_NAME: &str = "ash";

/// Everything assembly needs, gathered by the seam so this stays pure.
pub(crate) struct LaunchContext<'a> {
    pub metadata: &'a VersionMetadata,
    pub runtime: &'a Runtime,
    pub depot_root: &'a Path,
    pub game_directory: PathBuf,
    pub username: &'a str,
    pub profile_id: &'a str,
    pub access_token: &'a str,
    pub xuid: &'a str,
    pub client_id: &'a str,
    pub os: Os,
}

/// Where a version's native libraries are unpacked.
///
/// Under the depot, not the instance: natives belong to a version, so two
/// instances on 1.21.11 share one copy. Modern LWJGL extracts into this
/// directory itself, which is why the jars only have to be on the classpath.
pub(crate) fn natives_dir(depot_root: &Path, version_id: &str) -> PathBuf {
    depot_root.join(format!("versions/{version_id}/natives"))
}

/// Build the command that starts the game.
pub(crate) fn assemble(context: &LaunchContext) -> Result<Invocation, AshError> {
    let metadata = context.metadata;

    let main_class = metadata.main_class.clone().ok_or_else(|| AshError::LaunchUnsupported {
        version_id: metadata.id.clone(),
        detail: "its metadata names no main class".into(),
    })?;

    let natives = natives_dir(context.depot_root, &metadata.id);
    fs::create_dir_all(&natives)
        .map_err(|e| AshError::Storage { detail: format!("creating the natives directory: {e}") })?;

    let classpath = classpath(context)?;
    let separator = if context.os == Os::Windows { ";" } else { ":" };

    let assets_index = metadata.asset_index.as_ref().map(|index| index.id.clone());
    let variables = variables(context, &natives, &classpath, separator, assets_index.as_deref());

    let jvm = resolve(&jvm_entries(metadata, context.os), &variables);
    let game = resolve(&game_entries(metadata, context.os), &variables);

    let mut args = jvm;
    args.push(main_class);
    args.extend(game);

    Ok(Invocation {
        program: context.runtime.java_executable.clone(),
        args,
        working_directory: context.game_directory.clone(),
        // The access token is the only thing on this command line that must
        // never be shown, logged or sent to the UI.
        secrets: vec![context.access_token.to_owned()],
    })
}

/// Rule-permitted libraries in metadata order, then the version jar.
///
/// Order is not cosmetic: the client jar goes last so that a library never
/// shadows a game class, which is the order Mojang's own launcher uses.
fn classpath(context: &LaunchContext) -> Result<Vec<PathBuf>, AshError> {
    let mut entries: Vec<PathBuf> = Vec::new();
    let mut seen: Vec<String> = Vec::new();

    for library in version::select_libraries(&context.metadata.libraries, context.os) {
        let Some(artifact) = &library.downloads.artifact else {
            continue;
        };
        let Some(relative) = &artifact.path else {
            continue;
        };
        // One entry per path. A duplicate would not break the JVM, but it
        // makes the command line unreadable and hides real conflicts.
        if seen.iter().any(|s| s == relative) {
            continue;
        }
        seen.push(relative.clone());
        entries.push(context.depot_root.join(format!("libraries/{relative}")));
    }

    entries.push(context.depot_root.join(format!(
        "versions/{id}/{id}.jar",
        id = context.metadata.id
    )));

    Ok(entries)
}

fn variables(
    context: &LaunchContext,
    natives: &Path,
    classpath: &[PathBuf],
    separator: &str,
    assets_index: Option<&str>,
) -> HashMap<&'static str, String> {
    let joined = classpath
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join(separator);

    let mut vars = HashMap::new();
    vars.insert("auth_player_name", context.username.to_owned());
    vars.insert("version_name", context.metadata.id.clone());
    vars.insert("game_directory", context.game_directory.display().to_string());
    vars.insert("assets_root", context.depot_root.join("assets").display().to_string());
    vars.insert("assets_index_name", assets_index.unwrap_or_default().to_owned());
    vars.insert("auth_uuid", context.profile_id.to_owned());
    vars.insert("auth_access_token", context.access_token.to_owned());
    vars.insert("auth_xuid", context.xuid.to_owned());
    vars.insert("clientid", context.client_id.to_owned());
    vars.insert("user_type", "msa".to_owned());
    vars.insert(
        "version_type",
        context.metadata.version_type.clone().unwrap_or_else(|| "release".to_owned()),
    );
    vars.insert("natives_directory", natives.display().to_string());
    vars.insert("launcher_name", LAUNCHER_NAME.to_owned());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").to_owned());
    vars.insert("classpath", joined);
    vars.insert("classpath_separator", separator.to_owned());
    vars.insert("library_directory", context.depot_root.join("libraries").display().to_string());

    // Pre-1.13 shapes. Harmless on modern versions, which never ask.
    vars.insert("game_assets", context.depot_root.join("assets/virtual/legacy").display().to_string());
    vars.insert(
        "auth_session",
        format!("token:{}:{}", context.access_token, context.profile_id),
    );
    vars.insert("user_properties", "{}".to_owned());

    vars
}

/// The JVM arguments, with a fallback for metadata that predates the list.
fn jvm_entries(metadata: &VersionMetadata, os: Os) -> Vec<String> {
    if metadata.arguments.jvm.is_empty() {
        // What every pre-1.13 version needs and none of them state. Making
        // 1.8.9 actually run is #9; this is only so assembly has one shape.
        return vec![
            "-Djava.library.path=${natives_directory}".to_owned(),
            "-cp".to_owned(),
            "${classpath}".to_owned(),
        ];
    }
    version::resolve_arguments(&metadata.arguments.jvm, os)
}

fn game_entries(metadata: &VersionMetadata, os: Os) -> Vec<String> {
    if metadata.arguments.game.is_empty() {
        if let Some(legacy) = &metadata.minecraft_arguments {
            return legacy.split_whitespace().map(str::to_owned).collect();
        }
    }
    version::resolve_arguments(&metadata.arguments.game, os)
}

fn resolve(entries: &[String], variables: &HashMap<&'static str, String>) -> Vec<String> {
    entries.iter().map(|entry| substitute(entry, variables)).collect()
}

/// Replace every `${name}` ash knows, in one pass.
///
/// One pass, not a replace per variable: a substituted value is never
/// rescanned, so a game directory that happens to contain `${` cannot turn
/// into something else.
fn substitute(value: &str, variables: &HashMap<&'static str, String>) -> String {
    let mut out = String::with_capacity(value.len());
    let mut rest = value;

    while let Some(start) = rest.find("${") {
        out.push_str(&rest[..start]);
        let after = &rest[start + 2..];
        let Some(end) = after.find('}') else {
            // An unterminated `${` is just text.
            out.push_str(&rest[start..]);
            return out;
        };
        let name = &after[..end];
        match variables.get(name) {
            Some(value) => out.push_str(value),
            // Left visibly intact. An unknown placeholder silently becoming
            // an empty argument is how a launcher ends up passing
            // `--assetIndex ""` and blaming the game.
            None => {
                out.push_str("${");
                out.push_str(name);
                out.push('}');
            }
        }
        rest = &after[end + 1..];
    }

    out.push_str(rest);
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn vars() -> HashMap<&'static str, String> {
        let mut vars = HashMap::new();
        vars.insert("auth_player_name", "oogz".to_owned());
        vars.insert("classpath", "a.jar;b.jar".to_owned());
        vars
    }

    #[test]
    fn a_known_placeholder_is_replaced() {
        assert_eq!(substitute("${auth_player_name}", &vars()), "oogz");
        assert_eq!(substitute("-cp=${classpath}", &vars()), "-cp=a.jar;b.jar");
    }

    #[test]
    fn an_unknown_placeholder_is_left_visible() {
        // Emitting an empty string here is how a launcher passes
        // `--assetIndex ""` and then blames the game for the crash.
        assert_eq!(substitute("${quickPlayPath}", &vars()), "${quickPlayPath}");
    }

    #[test]
    fn a_substituted_value_is_not_rescanned() {
        let mut vars = vars();
        vars.insert("game_directory", "C:/${auth_player_name}".to_owned());
        assert_eq!(substitute("${game_directory}", &vars), "C:/${auth_player_name}");
    }

    #[test]
    fn text_around_and_between_placeholders_survives() {
        assert_eq!(substitute("-Dname=${auth_player_name}-x", &vars()), "-Dname=oogz-x");
        assert_eq!(substitute("no placeholders", &vars()), "no placeholders");
        assert_eq!(substitute("${", &vars()), "${");
    }
}
