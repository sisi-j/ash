//! The mod-loading layer an instance runs under.
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

use serde::{Deserialize, Serialize};

/// The mod-loading layer an instance runs under.
///
/// Only [`Loader::Vanilla`] exists so far. Fabric arrives with 1.21.11 and
/// Legacy Fabric with 1.8.9 - the two run the same Fabric Loader artifact and
/// differ in their intermediary, mappings and API, which is why they are two
/// loaders here rather than one with a flag.
///
/// Deliberately not a string. A loader ash cannot prepare is an instance that
/// can never launch, and the set of loaders is small, closed and ash's own -
/// so it is a type the compiler checks rather than a value to validate.
///
/// Deliberately no `Default`, either. A defaultable loader is a loader that
/// can be left unsaid, and `..Default::default()` would silently mean vanilla
/// the moment a second loader exists - which is the special case this type
/// was introduced to prevent. Reading an instance that predates loaders is
/// the one place a loader is not named, and it names its own default there.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Loader {
    /// The game as Mojang ships it, with no mod loader and no ash client.
    Vanilla,
}
