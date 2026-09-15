//! The `inheritsFrom` merge: combining a loader's version document with the
//! vanilla one it sits on top of.
//!
//! **This merge is specified nowhere.** It appears in no Mojang
//! documentation, in no Fabric documentation, and not in minecraft.wiki's
//! `client.json` article - the de-facto community reference, whose full
//! field tree names every other field and never mentions this one. The
//! official launcher plainly honours it, because writing a profile into
//! `versions/` and registering it is Fabric's entire shipping install path,
//! but no published contract describes what honouring it means.
//!
//! So this is reconstructed from observation, from two independent shipping
//! implementations that agree on the shape and disagree on the details. Do
//! not "correct" a rule here against intuition; each one is the way it is
//! because a real launcher does it that way and the alternative breaks a
//! real version target. The tests name which.

use std::collections::HashSet;

use crate::error::AshError;
use crate::version::{Arguments, Library, VersionMetadata};

/// How deep an inheritance chain may go before ash calls it a cycle.
///
/// Fabric never produces a nested profile - `inheritsFrom` is always the
/// vanilla version id - so anything beyond a couple of links is already
/// unexpected. The visited set below is what actually catches a cycle; this
/// only bounds a chain that is long without repeating.
const MAX_DEPTH: usize = 16;

/// Merge a child version document over the parent it inherits from.
///
/// The asymmetry in the middle is the part to not tidy up: **arguments are
/// parent-then-child, libraries are child-then-parent.** Both orderings are
/// load-bearing. Fabric's libraries have to precede vanilla's on the
/// classpath or the game loads vanilla's ASM and the loader dies; Fabric's
/// arguments have to follow vanilla's on the command line.
pub fn merge(parent: &VersionMetadata, child: &VersionMetadata) -> VersionMetadata {
    VersionMetadata {
        // The child's id names the merged result, always - that is what the
        // launcher knows the instance by.
        id: child.id.clone(),
        // Cleared: the merged document is the resolved one, and leaving it
        // set would make a second resolution pass inherit all over again.
        inherits_from: None,
        // `jar` is how the client jar is still found once `id` stops naming
        // one. A Fabric profile's id has no jar behind it - Fabric's own
        // installer deletes the file it creates - so this field, or failing
        // that the parent's id, is the only route to the real client jar.
        jar: child.jar.clone().or_else(|| parent.jar.clone()).or_else(|| Some(parent.id.clone())),
        main_class: child.main_class.clone().or_else(|| parent.main_class.clone()),
        asset_index: child.asset_index.clone().or_else(|| parent.asset_index.clone()),
        downloads: if child.downloads.client.is_some() {
            child.downloads.clone()
        } else {
            parent.downloads.clone()
        },
        java_version: child.java_version.clone().or_else(|| parent.java_version.clone()),
        minecraft_arguments: child
            .minecraft_arguments
            .clone()
            .or_else(|| parent.minecraft_arguments.clone()),
        version_type: child.version_type.clone().or_else(|| parent.version_type.clone()),
        logging: child.logging.clone().or_else(|| parent.logging.clone()),
        arguments: Arguments {
            game: [parent.arguments.game.clone(), child.arguments.game.clone()].concat(),
            jvm: [parent.arguments.jvm.clone(), child.arguments.jvm.clone()].concat(),
        },
        libraries: merge_libraries(&parent.libraries, &child.libraries),
    }
}

/// Concatenate child-first, then keep one entry per `group:artifact` and
/// classifier.
///
/// **Replacement happens here, before any rule is evaluated, and that
/// ordering is the whole of it.** A loader replaces a vanilla library by
/// naming the same `group:artifact` at a different version - the version is
/// deliberately not part of the key - and the replacement has to win
/// outright rather than being kept alongside as a platform variant.
///
/// HMCL keys on `group:artifact` but compares rules first and keeps both
/// when they differ, which is exactly when a loader is replacing something,
/// and then it needs a hard-coded `repairDuplicateAsm` to undo the damage on
/// one specific loader. Doing the replacement first means ash needs no such
/// special case, and a rule-less loader entry correctly displaces a vanilla
/// entry whose rules would otherwise have kept it.
fn merge_libraries(parent: &[Library], child: &[Library]) -> Vec<Library> {
    let mut merged: Vec<Library> = Vec::new();
    let mut seen: HashSet<(String, Option<String>)> = HashSet::new();

    for library in child.iter().chain(parent.iter()) {
        let key = (library.group_artifact().to_owned(), library.classifier().map(|c| c.to_owned()));
        if seen.insert(key) {
            merged.push(library.clone());
        }
    }
    merged
}

/// Follow `inheritsFrom` to the root and merge the whole chain.
///
/// `load` fetches a document by id. Nesting is real - at least one shipping
/// launcher resolves the parent's own parent before merging - so this
/// recurses rather than assuming one level, and **it resolves each parent
/// fully before merging it**. Merging on the way down instead would clear
/// `inheritsFrom` at the first step and silently drop every link above it.
///
/// A cycle is treated as something that happens, not something that cannot.
/// HMCL carries a `resolvedSoFar` set for exactly this and logs "Found
/// circular dependency instances" when it fires. ash fails instead of
/// silently breaking the link: every document in a chain ash resolves is one
/// ash itself wrote, so a cycle means something is wrong that quietly
/// launching a half-merged profile would hide.
pub fn resolve(
    leaf: &VersionMetadata,
    load: &dyn Fn(&str) -> Result<VersionMetadata, AshError>,
) -> Result<VersionMetadata, AshError> {
    let mut visited: HashSet<String> = HashSet::new();
    visited.insert(leaf.id.clone());
    resolve_from(leaf, load, &mut visited, 0)
}

fn resolve_from(
    leaf: &VersionMetadata,
    load: &dyn Fn(&str) -> Result<VersionMetadata, AshError>,
    visited: &mut HashSet<String>,
    depth: usize,
) -> Result<VersionMetadata, AshError> {
    let Some(parent_id) = leaf.inherits_from.clone() else {
        return Ok(leaf.clone());
    };

    if depth >= MAX_DEPTH {
        return Err(AshError::Malformed {
            url: leaf.id.clone(),
            detail: "version metadata inherits too many levels deep".into(),
        });
    }
    // A repeat is a cycle. Checked before loading, so a document that
    // inherits from itself fails here rather than recursing until the depth
    // bound catches it and reports the wrong thing.
    if !visited.insert(parent_id.clone()) {
        return Err(AshError::Malformed {
            url: parent_id.clone(),
            detail: format!("version metadata inherits from {parent_id} in a cycle"),
        });
    }

    let parent = load(&parent_id)?;
    let parent = resolve_from(&parent, load, visited, depth + 1)?;
    Ok(merge(&parent, leaf))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::version::{self, Os};

    fn doc(json: &str) -> VersionMetadata {
        serde_json::from_str(json).expect("a version document")
    }

    /// Vanilla 1.21.11, cut down to the fields the merge has an opinion on.
    fn vanilla() -> VersionMetadata {
        doc(r#"{
            "id": "1.21.11",
            "type": "release",
            "mainClass": "net.minecraft.client.main.Main",
            "assetIndex": {"id":"26","sha1":"aa","size":1,"url":"https://a/26.json"},
            "javaVersion": {"component":"java-runtime-delta","majorVersion":21},
            "downloads": {"client":{"sha1":"bb","size":2,"url":"https://a/client.jar"}},
            "libraries": [
                {"name":"com.mojang:logging:1.5.10"},
                {"name":"org.ow2.asm:asm:9.6"}
            ],
            "arguments": {
                "game": ["--username","${auth_player_name}"],
                "jvm": ["-cp","${classpath}"]
            }
        }"#)
    }

    /// The exact seven-field shape Fabric Meta emits.
    fn fabric() -> VersionMetadata {
        doc(r#"{
            "id": "fabric-loader-0.19.5-1.21.11",
            "inheritsFrom": "1.21.11",
            "type": "release",
            "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "arguments": {
                "game": [],
                "jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "]
            },
            "libraries": [
                {"name":"org.ow2.asm:asm:9.10.1"},
                {"name":"net.fabricmc:fabric-loader:0.19.5"}
            ]
        }"#)
    }

    fn merged() -> VersionMetadata {
        merge(&vanilla(), &fabric())
    }

    fn names(libraries: &[Library]) -> Vec<&str> {
        libraries.iter().map(|l| l.name.as_str()).collect()
    }

    #[test]
    fn a_field_the_child_leaves_out_is_inherited() {
        let merged = merged();

        // A loader document carries none of this, and the game cannot start
        // without any of it.
        assert_eq!(merged.asset_index.expect("asset index").id, "26");
        assert_eq!(merged.java_version.expect("java version").component, "java-runtime-delta");
        assert_eq!(merged.downloads.client.expect("client jar").url, "https://a/client.jar");
    }

    #[test]
    fn a_field_the_child_sets_overrides_the_parent() {
        // The whole point of the loader: the JVM starts Knot, not Mojang's
        // entry point, and Knot starts the game.
        assert_eq!(
            merged().main_class.as_deref(),
            Some("net.fabricmc.loader.impl.launch.knot.KnotClient")
        );
    }

    #[test]
    fn arguments_are_concatenated_with_the_parents_first() {
        let merged = merged();

        assert_eq!(
            version::resolve_arguments(&merged.arguments.game, Os::Windows),
            ["--username", "${auth_player_name}"]
        );
        // Order matters on the command line: vanilla's arguments, then the
        // loader's.
        assert_eq!(
            version::resolve_arguments(&merged.arguments.jvm, Os::Windows),
            ["-cp", "${classpath}", "-DFabricMcEmu= net.minecraft.client.main.Main "]
        );
    }

    #[test]
    fn libraries_are_concatenated_with_the_childs_first() {
        // The reverse of arguments, and deliberately so: Fabric's libraries
        // have to precede vanilla's on the classpath.
        let merged = merged();

        assert_eq!(names(&merged.libraries)[0], "org.ow2.asm:asm:9.10.1");
        assert!(
            names(&merged.libraries).contains(&"com.mojang:logging:1.5.10"),
            "vanilla's own libraries still have to be there: {:?}",
            names(&merged.libraries)
        );
    }

    #[test]
    fn a_library_the_child_names_again_replaces_the_parents_version() {
        let merged = merged();
        let asm: Vec<&str> = names(&merged.libraries)
            .into_iter()
            .filter(|n| n.starts_with("org.ow2.asm:asm:"))
            .collect();

        // Both entries are `org.ow2.asm:asm` at different versions. Keeping
        // both would put ASM 9.6 and 9.10.1 on one classpath, and whichever
        // the JVM happened to load first would decide whether the loader
        // starts at all.
        assert_eq!(asm, ["org.ow2.asm:asm:9.10.1"], "the version is not part of the merge key");
    }

    #[test]
    fn a_rule_on_the_parents_entry_does_not_save_it_from_replacement() {
        // This is the ordering that matters: replace, *then* evaluate rules.
        // A loader's replacement entry carries no rules, and the vanilla
        // entries it displaces both do. HMCL compares rules first, keeps
        // both when they differ, and then needs a hard-coded repair pass to
        // undo it. Getting the order right needs no special case.
        let parent = doc(r#"{"id":"1.8.9","libraries":[
                {"name":"org.lwjgl.lwjgl:lwjgl:2.9.4-nightly","rules":[
                    {"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]},
                {"name":"org.lwjgl.lwjgl:lwjgl:2.9.2-nightly","rules":[
                    {"action":"allow","os":{"name":"osx"}}]}
            ]}"#);
        let child = doc(r#"{"id":"loader-1.8.9","inheritsFrom":"1.8.9","libraries":[
                {"name":"org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17"}
            ]}"#);

        let merged = merge(&parent, &child);

        assert_eq!(names(&merged.libraries), ["org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17"]);
        // And it survives rule evaluation on both platforms, which is the
        // player-visible half: two LWJGL 2 jars on one classpath is a game
        // that does not start.
        for os in [Os::Windows, Os::MacOs] {
            assert_eq!(version::select_libraries(&merged.libraries, os).len(), 1, "on {os:?}");
        }
    }

    #[test]
    fn a_jar_and_its_natives_are_not_taken_for_two_versions_of_one_library() {
        // Same `group:artifact`, different classifier. Keying on that alone
        // would drop the natives and leave the game with a library it
        // cannot load.
        let parent = doc(r#"{"id":"1.8.9","libraries":[
                {"name":"org.lwjgl.lwjgl:lwjgl-platform:2.9.4:natives-windows"},
                {"name":"org.lwjgl.lwjgl:lwjgl-platform:2.9.4"}
            ]}"#);
        let child = doc(r#"{"id":"c","inheritsFrom":"1.8.9","libraries":[]}"#);

        assert_eq!(merge(&parent, &child).libraries.len(), 2);
    }

    #[test]
    fn the_merged_document_still_names_a_client_jar() {
        // `id` is now a profile name with no jar behind it - Fabric's own
        // installer deletes the file it creates - so the classpath's last
        // entry has to come from somewhere else.
        assert_eq!(merged().jar.as_deref(), Some("1.21.11"));
        assert_eq!(merged().inherits_from, None, "a resolved document inherits from nothing");
    }

    // ---- resolution --------------------------------------------------------

    fn chain(
        documents: Vec<VersionMetadata>,
    ) -> impl Fn(&str) -> Result<VersionMetadata, AshError> {
        move |id: &str| {
            documents
                .iter()
                .find(|d| d.id == id)
                .cloned()
                .ok_or_else(|| AshError::UnknownVersion { version_id: id.to_owned() })
        }
    }

    #[test]
    fn inheritance_nests() {
        // Fabric never emits a nested profile, but at least one shipping
        // launcher resolves them, and merging on the way down instead of the
        // way up drops every link above the first.
        let root = doc(r#"{"id":"root","mainClass":"Root","libraries":[{"name":"g:root:1"}]}"#);
        let middle =
            doc(r#"{"id":"middle","inheritsFrom":"root","libraries":[{"name":"g:middle:1"}]}"#);
        let leaf =
            doc(r#"{"id":"leaf","inheritsFrom":"middle","libraries":[{"name":"g:leaf:1"}]}"#);

        let resolved = resolve(&leaf, &chain(vec![root, middle])).expect("resolves");

        assert_eq!(resolved.id, "leaf");
        assert_eq!(
            resolved.main_class.as_deref(),
            Some("Root"),
            "the root's field reached the leaf"
        );
        assert_eq!(names(&resolved.libraries), ["g:leaf:1", "g:middle:1", "g:root:1"]);
    }

    #[test]
    fn a_cycle_is_an_error_rather_than_a_hang() {
        let a = doc(r#"{"id":"a","inheritsFrom":"b"}"#);
        let b = doc(r#"{"id":"b","inheritsFrom":"a"}"#);

        let err = resolve(&a, &chain(vec![a.clone(), b])).expect_err("a cycle is refused");

        assert_eq!(err.kind(), "malformed");
    }

    #[test]
    fn a_document_that_inherits_from_itself_is_a_cycle() {
        let a = doc(r#"{"id":"a","inheritsFrom":"a"}"#);

        let err = resolve(&a, &chain(vec![a.clone()])).expect_err("refused");

        assert_eq!(err.kind(), "malformed");
    }

    #[test]
    fn a_missing_parent_is_reported_rather_than_silently_dropped() {
        // Resolving half a chain would produce a document with no main class
        // and no client jar, and the failure would surface much later as
        // something unrecognisable.
        let leaf = doc(r#"{"id":"leaf","inheritsFrom":"gone"}"#);

        let err = resolve(&leaf, &chain(vec![])).expect_err("refused");

        assert_eq!(err.kind(), "unknown_version");
    }
}
