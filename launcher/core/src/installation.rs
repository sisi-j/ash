//! Whether an installation of ash can start a modded instance: the things only
//! a real installation can get wrong, checked from where the installed
//! launcher itself looks.
//!
//! A launch on 8 October 2026 failed from the installed ash and nowhere else:
//! the installation's folder came back from Windows in its verbatim form,
//! `\\?\C:\...`, and that reached the game in `-Dfabric.addMods`, which 1.8.9's
//! Java 8 cannot read. Dev builds and the tests never see that form. So the
//! installed launcher can check itself - `ash --self-check` - and CI installs
//! the real installer and runs it.

use std::path::{Path, PathBuf};

use crate::config::Config;
use crate::launch;

/// What the check found: a line per thing checked, and whether all was well.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InstallationCheck {
    /// Each finding as a line of the report, starting `ok:` or `problem:`.
    pub lines: Vec<String>,
    pub ok: bool,
}

impl InstallationCheck {
    /// The report, one finding to a line.
    pub fn report(&self) -> String {
        let mut report = String::from("ash self-check\n");
        for line in &self.lines {
            report.push_str(line);
            report.push('\n');
        }
        report.push_str(if self.ok { "result: ok\n" } else { "result: problems found\n" });
        report
    }
}

/// Checks, for every version target whose pin names a client jar, that the
/// jar is in the installation and is a jar, and that the argument a launch
/// would hand the loader for it names it by a path Java reads. Then that
/// ash's data folder can be written to.
pub(crate) fn check(config: &Config) -> InstallationCheck {
    let mut lines = Vec::new();
    let mut ok = true;
    let separator = if cfg!(windows) { ";" } else { ":" };
    for pin in config.loaders {
        let Some(file_name) = pin.client_jar else { continue };
        let jar = config.client_root.join(file_name);
        let argument = launch::add_mods_argument(std::slice::from_ref(&jar), separator);
        match problem_with(&jar, &argument) {
            None => lines.push(format!("ok: {} client, handed over as {argument}", pin.version_id)),
            Some(problem) => {
                ok = false;
                lines.push(format!("problem: {} client: {problem} ({argument})", pin.version_id));
            }
        }
    }
    match writable(&config.data_root) {
        Ok(()) => lines.push("ok: the data folder can be written to".to_owned()),
        Err(problem) => {
            ok = false;
            lines.push(format!("problem: the data folder: {problem}"));
        }
    }
    InstallationCheck { lines, ok }
}

fn problem_with(jar: &Path, argument: &str) -> Option<String> {
    let path = argument.trim_start_matches("-Dfabric.addMods=");
    if path.contains(r"\\?\") {
        return Some(
            "its path reaches Java in Windows' verbatim form, which Java cannot read".to_owned(),
        );
    }
    if !is_absolute_for_java(path) {
        return Some("its path reaches Java as a relative path".to_owned());
    }
    let bytes = match std::fs::read(jar) {
        Ok(bytes) => bytes,
        Err(_) => return Some("it is not in the installation".to_owned()),
    };
    // A jar is a zip, and every zip starts with a local file header.
    if !bytes.starts_with(b"PK\x03\x04") {
        return Some("it is in the installation but is not a jar".to_owned());
    }
    None
}

/// Absolute as Java on this system reads it: a drive or a share on Windows,
/// the root elsewhere.
fn is_absolute_for_java(path: &str) -> bool {
    if cfg!(windows) {
        let bytes = path.as_bytes();
        let drive = bytes.len() > 2
            && bytes[0].is_ascii_alphabetic()
            && bytes[1] == b':'
            && bytes[2] == b'\\';
        drive || path.starts_with(r"\\")
    } else {
        path.starts_with('/')
    }
}

fn writable(folder: &Path) -> Result<(), String> {
    std::fs::create_dir_all(folder).map_err(|e| format!("could not create it ({})", e.kind()))?;
    let probe: PathBuf = folder.join(".ash-self-check");
    std::fs::write(&probe, b"ash").map_err(|e| format!("could not write in it ({})", e.kind()))?;
    let _ = std::fs::remove_file(&probe);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn installed(tmp: &Path, root: PathBuf) -> Config {
        let config = Config { client_root: root, ..Config::rooted_at(tmp) };
        std::fs::create_dir_all(&config.client_root).unwrap();
        for pin in config.loaders {
            if let Some(jar) = pin.client_jar {
                std::fs::write(config.client_root.join(jar), b"PK\x03\x04 a jar").unwrap();
            }
        }
        config
    }

    #[test]
    fn a_whole_installation_passes() {
        let tmp = tempfile::tempdir().unwrap();
        let config = installed(tmp.path(), tmp.path().join("client"));
        let check = check(&config);
        assert!(check.ok, "{}", check.report());
        assert!(
            check.lines.iter().any(|l| l.starts_with("ok: 1.8.9 client")),
            "{}",
            check.report()
        );
    }

    #[test]
    fn a_missing_jar_is_named() {
        let tmp = tempfile::tempdir().unwrap();
        let config = installed(tmp.path(), tmp.path().join("client"));
        std::fs::remove_file(config.client_root.join("ash-client-1.8.9.jar")).unwrap();
        let check = check(&config);
        assert!(!check.ok);
        assert!(
            check.report().contains("problem: 1.8.9 client: it is not in the installation"),
            "{}",
            check.report()
        );
    }

    #[test]
    fn a_file_that_is_not_a_jar_is_named() {
        let tmp = tempfile::tempdir().unwrap();
        let config = installed(tmp.path(), tmp.path().join("client"));
        std::fs::write(config.client_root.join("ash-client-1.21.11.jar"), b"<html>").unwrap();
        let check = check(&config);
        assert!(!check.ok);
        assert!(check
            .report()
            .contains("problem: 1.21.11 client: it is in the installation but is not a jar"));
    }

    /// The installed ash's own case: the installation folder in verbatim
    /// form still reaches Java as a plain path, and passes.
    #[cfg(windows)]
    #[test]
    fn an_installation_in_verbatim_form_passes() {
        let tmp = tempfile::tempdir().unwrap();
        let verbatim = PathBuf::from(format!(r"\\?\{}", tmp.path().join("client").display()));
        let check = check(&installed(tmp.path(), verbatim));
        assert!(check.ok, "{}", check.report());
        assert!(!check.report().contains(r"\\?\"), "{}", check.report());
    }
}
