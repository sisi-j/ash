use std::path::{Path, PathBuf};

use crate::loader::{self, LoaderPin};

/// Where ash keeps things on this machine.
///
/// These are values, not a seam. Nothing in ash-core resolves a path from the
/// environment on its own, which is precisely what lets a test point the whole
/// library at a temporary directory.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Config {
    /// The single content-addressed pool every instance draws from.
    pub depot_root: PathBuf,
    /// The parent of every instance's isolated game directory.
    pub instances_root: PathBuf,
    /// ash's own non-secret state: the account list, settings.
    ///
    /// Secrets never live here - refresh tokens go to the OS credential
    /// store through [`crate::credentials::CredentialStore`].
    pub data_root: PathBuf,
    /// The loaders ash can install, per version target.
    ///
    /// A value for the same reason the roots above are. These are ash's own
    /// build-time data, and every artifact one names is verified against a
    /// hash recorded beside it - so a test that could not point this
    /// somewhere else would have to serve several megabytes of real
    /// third-party jars to exercise a modded instance at all.
    ///
    /// Defaults to [`loader::PINS`]; nothing but a test has reason to
    /// change it.
    pub loaders: &'static [LoaderPin],
    /// Where the ash client jars that shipped with this build live.
    ///
    /// The odd one out: the other roots are ash's own storage and this one is
    /// part of the installation. The client ships inside the installer so
    /// that the launcher and the client can never be version-skewed, which
    /// means it sits beside the executable rather than under the data base -
    /// so the adapter points this at the installed resources, and
    /// [`Config::rooted_at`]'s guess is only good enough for a test that puts
    /// everything in one temporary directory.
    pub client_root: PathBuf,
}

impl Config {
    /// The layout ash uses on a real machine, given a base directory.
    ///
    /// Choosing that base is the caller's job - on Windows it is the app data
    /// directory, and under test it is a `tempfile::TempDir`.
    ///
    /// [`Config::client_root`] is the exception and is only a placeholder
    /// here: on a real machine the client jars are part of the installation,
    /// not of ash's storage, so the adapter overwrites it.
    /// The only constructor, deliberately. Four roots as positional
    /// arguments is four paths that can be swapped and still compile, and
    /// every field here is public - so a caller wanting a different layout
    /// names the field it is changing: `Config { depot_root: elsewhere,
    /// ..Config::rooted_at(base) }`, which is what the tests already do.
    pub fn rooted_at(base: impl AsRef<Path>) -> Self {
        let base = base.as_ref();
        Self {
            depot_root: base.join("depot"),
            instances_root: base.join("instances"),
            data_root: base.join("data"),
            loaders: loader::PINS,
            client_root: base.join("client"),
        }
    }
}
