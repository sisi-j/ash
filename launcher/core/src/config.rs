use std::path::{Path, PathBuf};

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
}

impl Config {
    pub fn new(
        depot_root: impl Into<PathBuf>,
        instances_root: impl Into<PathBuf>,
        data_root: impl Into<PathBuf>,
    ) -> Self {
        Self {
            depot_root: depot_root.into(),
            instances_root: instances_root.into(),
            data_root: data_root.into(),
        }
    }

    /// The layout ash uses on a real machine, given a base directory.
    ///
    /// Choosing that base is the caller's job - on Windows it is the app data
    /// directory, and under test it is a `tempfile::TempDir`.
    pub fn rooted_at(base: impl AsRef<Path>) -> Self {
        let base = base.as_ref();
        Self::new(base.join("depot"), base.join("instances"), base.join("data"))
    }
}
