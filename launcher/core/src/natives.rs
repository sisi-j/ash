//! Unpacking native libraries.
//!
//! Whether a version needs this is a question its own metadata answers: a
//! library carrying a `natives` map is one whose jar has to become real
//! files on disk, because LWJGL 2 loads them off `java.library.path`.
//! LWJGL 3 reads them straight out of the classpath and modern manifests
//! carry no `natives` map, so the same code does nothing there. There is no
//! version check, and there must not be one.

use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

use crate::error::AshError;
use crate::version::{self, Library, Os, VersionMetadata};

/// Where a version's native libraries are unpacked.
///
/// Under the depot rather than the instance: natives belong to a version, so
/// two instances on 1.8.9 share one copy.
pub(crate) fn directory(depot_root: &Path, version_id: &str) -> PathBuf {
    depot_root.join(format!("versions/{version_id}/natives"))
}

/// Unpack every native jar this version needs on this platform.
///
/// Idempotent: a file already unpacked at the right size is left alone, so
/// the second launch of an instance does no work.
pub(crate) fn extract(
    depot_root: &Path,
    metadata: &VersionMetadata,
    os: Os,
) -> Result<(), AshError> {
    let target = directory(depot_root, &metadata.id);
    fs::create_dir_all(&target).map_err(AshError::writing("creating the natives directory"))?;

    for library in version::select_libraries(&metadata.libraries, os) {
        let Some(native) = library.natives_for(os) else {
            continue;
        };
        let Some(relative) = &native.path else {
            continue;
        };
        unpack(&depot_root.join(format!("libraries/{relative}")), &target, &exclusions(library))?;
    }

    Ok(())
}

fn exclusions(library: &Library) -> Vec<String> {
    library.extract.as_ref().map(|e| e.exclude.clone()).unwrap_or_default()
}

fn unpack(jar: &Path, target: &Path, exclude: &[String]) -> Result<(), AshError> {
    let file = fs::File::open(jar).map_err(AshError::writing("opening a native library archive"))?;
    let mut archive = zip::ZipArchive::new(file)
        .map_err(|e| AshError::Storage { detail: format!("reading a native library archive: {e}") })?;

    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).map_err(|e| AshError::Storage {
            detail: format!("reading a native library entry: {e}"),
        })?;

        if entry.is_dir() {
            continue;
        }

        // `enclosed_name` refuses absolute paths and anything climbing out
        // with `..`. An archive that tried would be writing wherever it
        // liked on the player's machine, so a rejected entry is skipped
        // rather than sanitised into something plausible.
        let Some(name) = entry.enclosed_name() else {
            continue;
        };
        let Some(relative) = name.to_str() else {
            continue;
        };
        let relative = relative.replace('\\', "/");

        if exclude.iter().any(|prefix| relative.starts_with(prefix.as_str())) {
            continue;
        }

        let destination = target.join(&relative);
        if let Ok(existing) = fs::metadata(&destination) {
            if existing.len() == entry.size() {
                continue;
            }
        }

        if let Some(parent) = destination.parent() {
            fs::create_dir_all(parent).map_err(AshError::writing("creating a natives directory"))?;
        }

        let mut bytes = Vec::with_capacity(entry.size() as usize);
        entry.read_to_end(&mut bytes).map_err(AshError::writing("unpacking a native library"))?;
        fs::write(&destination, &bytes).map_err(AshError::writing("writing a native library"))?;
    }

    Ok(())
}
