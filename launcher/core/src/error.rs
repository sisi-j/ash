use thiserror::Error;

/// Everything ash-core can fail with.
///
/// The UI never sees a raw error string. The adapter maps a variant to a
/// message via [`AshError::user_message`], and [`AshError::kind`] gives it
/// something stable to branch on that is not the display text.
///
/// The sign-in variants are deliberately fine-grained. Several of them are
/// unfixable by retrying, and telling a banned player to "try again" or a
/// Game Pass player that they do not own the game would both be wrong.
#[derive(Debug, Error)]
pub enum AshError {
    // ---- transport ----
    #[error("transport failure talking to {url}: {detail}")]
    Transport { url: String, detail: String },

    #[error("{url} answered {status}")]
    UnexpectedStatus { url: String, status: u16 },

    #[error("could not parse the response from {url}: {detail}")]
    Malformed { url: String, detail: String },

    // ---- local storage ----
    #[error("storage problem: {detail}")]
    Storage { detail: String },

    #[error("out of disk space")]
    OutOfSpace,

    #[error("credential store problem: {detail}")]
    Credential { detail: String },

    #[error("{path} did not match its published hash")]
    VerificationFailed { path: String },

    #[error("{path} disappeared while ash was writing it")]
    FileVanished { path: String },

    #[error("cancelled")]
    Cancelled,

    // ---- instances ----
    #[error("no instance with id {id}")]
    InstanceNotFound { id: String },

    #[error("invalid instance name: {detail}")]
    InvalidInstanceName { detail: String },

    #[error("the catalogue has no version {version_id}")]
    UnknownVersion { version_id: String },

    #[error("Mojang publishes no {component} runtime for {platform}")]
    RuntimeUnavailable { component: String, platform: String },

    // ---- sign-in ----
    #[error("no sign-in is in progress")]
    NoSignInPending,

    #[error("the sign-in code expired")]
    SignInExpired,

    #[error("the sign-in was declined")]
    SignInDeclined,

    #[error("sign-in failed: {detail}")]
    SignInFailed { detail: String },

    #[error("the stored session is no longer valid")]
    SessionExpired,

    // ---- Xbox ----
    #[error("this Microsoft account has no Xbox profile")]
    XboxNoAccount,

    #[error("this Xbox account is banned")]
    XboxBanned,

    #[error("Xbox Live is unavailable in this country")]
    XboxRegionUnavailable,

    #[error("this Xbox account needs adult verification")]
    XboxAdultVerificationRequired,

    #[error("this child account must be added to a Family group")]
    XboxChildAccount,

    #[error("Xbox authorization failed with code {code}")]
    XboxOther { code: u64 },

    // ---- Minecraft services ----
    #[error("this application id is not on Mojang's allow list")]
    NotAllowListed,

    #[error("this account does not own Minecraft: Java Edition")]
    NotEntitled,

    #[error("this account has no Minecraft profile yet")]
    ProfileUnavailable,

    // ---- accounts ----
    #[error("no account is signed in")]
    NoAccountSelected,

    #[error("ash knows no account {profile_id}")]
    AccountNotFound { profile_id: String },

    // ---- settings ----
    #[error("invalid setting: {detail}")]
    InvalidSetting { detail: String },

    // ---- launching ----
    #[error("ash cannot build a command line for {version_id}: {detail}")]
    LaunchUnsupported { version_id: String, detail: String },

    #[error("could not start the game: {detail}")]
    LaunchFailed { detail: String },

    #[error("instance {id} is already running")]
    AlreadyRunning { id: String },
}

/// Windows and Unix both have a distinct error for "the volume is full", and
/// it is the one storage failure a player can act on directly.
fn out_of_space(e: &std::io::Error) -> bool {
    match e.raw_os_error() {
        // ERROR_DISK_FULL, and ERROR_HANDLE_DISK_FULL from the older APIs.
        Some(112) | Some(39) if cfg!(windows) => true,
        // ENOSPC.
        Some(28) if !cfg!(windows) => true,
        _ => false,
    }
}

impl AshError {
    /// Map a filesystem failure, recognising the ones worth their own words.
    ///
    /// A closure so call sites read `.map_err(AshError::writing("..."))`
    /// rather than repeating the match, which is how one of them would end
    /// up being the site that forgets.
    pub(crate) fn writing(context: &'static str) -> impl Fn(std::io::Error) -> AshError {
        move |e| {
            if out_of_space(&e) {
                AshError::OutOfSpace
            } else {
                AshError::Storage { detail: format!("{context}: {e}") }
            }
        }
    }

    /// A stable machine-readable discriminant. Safe to match on in the UI;
    /// unlike the Display text, it is part of the contract.
    pub fn kind(&self) -> &'static str {
        match self {
            AshError::Transport { .. } => "transport",
            AshError::UnexpectedStatus { .. } => "unexpected_status",
            AshError::Malformed { .. } => "malformed",
            AshError::Storage { .. } => "storage",
            AshError::OutOfSpace => "out_of_space",
            AshError::Credential { .. } => "credential",
            AshError::VerificationFailed { .. } => "verification_failed",
            AshError::FileVanished { .. } => "file_vanished",
            AshError::Cancelled => "cancelled",
            AshError::InstanceNotFound { .. } => "instance_not_found",
            AshError::InvalidInstanceName { .. } => "invalid_instance_name",
            AshError::UnknownVersion { .. } => "unknown_version",
            AshError::RuntimeUnavailable { .. } => "runtime_unavailable",
            AshError::NoSignInPending => "no_sign_in_pending",
            AshError::SignInExpired => "sign_in_expired",
            AshError::SignInDeclined => "sign_in_declined",
            AshError::SignInFailed { .. } => "sign_in_failed",
            AshError::SessionExpired => "session_expired",
            AshError::XboxNoAccount => "xbox_no_account",
            AshError::XboxBanned => "xbox_banned",
            AshError::XboxRegionUnavailable => "xbox_region_unavailable",
            AshError::XboxAdultVerificationRequired => "xbox_adult_verification_required",
            AshError::XboxChildAccount => "xbox_child_account",
            AshError::XboxOther { .. } => "xbox_other",
            AshError::NotAllowListed => "not_allow_listed",
            AshError::NotEntitled => "not_entitled",
            AshError::ProfileUnavailable => "profile_unavailable",
            AshError::NoAccountSelected => "no_account_selected",
            AshError::AccountNotFound { .. } => "account_not_found",
            AshError::InvalidSetting { .. } => "invalid_setting",
            AshError::LaunchUnsupported { .. } => "launch_unsupported",
            AshError::LaunchFailed { .. } => "launch_failed",
            AshError::AlreadyRunning { .. } => "already_running",
        }
    }

    /// What a player should be told. Never leaks a URL, a path, a token, or a
    /// library's error text.
    pub fn user_message(&self) -> String {
        match self {
            AshError::Transport { .. } => {
                "Could not reach the server. Check your connection and try again.".into()
            }
            AshError::UnexpectedStatus { .. } => {
                "The server returned an unexpected response. Try again shortly.".into()
            }
            AshError::Malformed { .. } => {
                "The response could not be understood. This is likely a bug in ash.".into()
            }
            AshError::Storage { .. } => {
                "ash could not write to its own data folder. Check disk space and permissions."
                    .into()
            }
            // Worth its own message: "check disk space and permissions" is
            // a guess, and this is the one case where ash knows.
            AshError::OutOfSpace => "The disk is full. Free some space and try again.".into(),
            AshError::Credential { .. } => {
                "ash could not use the Windows credential store. Your sign-in was not saved.".into()
            }
            // Names the file. It is a path inside ash's own depot, not
            // anything of the player's, and it is the one thing they need in
            // order to add an exclusion in their antivirus.
            AshError::VerificationFailed { path } => {
                format!(
                    "A downloaded file kept arriving corrupted: {path}. Check your connection, \
                     and any antivirus or proxy that might be altering downloads."
                )
            }
            AshError::FileVanished { path } => {
                format!(
                    "{path} was removed straight after ash downloaded it. Antivirus software \
                     usually does this; adding ash's folder as an exclusion will fix it."
                )
            }
            AshError::Cancelled => "Cancelled.".into(),
            AshError::InstanceNotFound { .. } => {
                "That instance no longer exists. It may have been deleted outside ash.".into()
            }
            AshError::InvalidInstanceName { .. } => {
                "That name can't be used. Give the instance a name with at least one character."
                    .into()
            }
            AshError::UnknownVersion { .. } => {
                "Mojang no longer publishes that version, so ash can't prepare it.".into()
            }
            AshError::RuntimeUnavailable { .. } => {
                "Mojang doesn't publish a Java runtime this version can use on your platform."
                    .into()
            }
            AshError::NoSignInPending => "There is no sign-in to complete. Start again.".into(),
            AshError::SignInExpired => {
                "That code expired before it was used. Start again for a fresh one.".into()
            }
            AshError::SignInDeclined => {
                "The sign-in was declined. Start again if that wasn't deliberate.".into()
            }
            AshError::SignInFailed { .. } => "Sign-in failed. Try again.".into(),
            AshError::SessionExpired => {
                "Your saved sign-in is no longer valid. Sign in again.".into()
            }
            AshError::XboxNoAccount => {
                "This Microsoft account has no Xbox profile. Create one at xbox.com, then sign \
                 in again."
                    .into()
            }
            AshError::XboxBanned => "This Xbox account is banned and can't be used to play.".into(),
            AshError::XboxRegionUnavailable => {
                "Xbox Live isn't available in this account's country, so it can't be used to play."
                    .into()
            }
            AshError::XboxAdultVerificationRequired => {
                "This account needs adult verification before it can be used.".into()
            }
            AshError::XboxChildAccount => {
                "This child account must be added to a Microsoft Family group before it can be \
                 used."
                    .into()
            }
            AshError::XboxOther { .. } => {
                "Xbox Live refused this account. Check it at xbox.com.".into()
            }
            // Phrased for whoever is running ash, not the player: a player can
            // do nothing about this, and it is the project's own gate.
            AshError::NotAllowListed => {
                "ash isn't yet approved for Mojang's game service API, so sign-in can't complete."
                    .into()
            }
            AshError::NotEntitled => "This account doesn't own Minecraft: Java Edition.".into(),
            AshError::ProfileUnavailable => {
                "This account has no Minecraft profile yet. Sign into the official Minecraft \
                 launcher once to create one, then come back."
                    .into()
            }
            AshError::NoAccountSelected => {
                "Sign in with a Microsoft account before launching.".into()
            }
            // Not "your session expired": nothing expired, the account is
            // simply gone, and telling a player to sign in again would send
            // them to fix something that is not broken.
            AshError::AccountNotFound { .. } => "That account is no longer on this machine.".into(),
            // The detail is the whole message here: it names the field and
            // the range, which is what the player needs to fix it, and it
            // contains nothing they did not just type.
            AshError::InvalidSetting { detail } => {
                let mut message = detail.clone();
                if let Some(first) = message.get_mut(0..1) {
                    first.make_ascii_uppercase();
                }
                format!("{message}.")
            }
            // Names the version. Which one it is is the whole point, and it
            // is not a secret.
            AshError::LaunchUnsupported { version_id, .. } => {
                format!("ash can't launch {version_id} yet.")
            }
            AshError::LaunchFailed { .. } => {
                "The game didn't start. ash's Java runtime may be damaged - preparing the \
                 instance again will replace it."
                    .into()
            }
            AshError::AlreadyRunning { .. } => "That instance is already running.".into(),
        }
    }

    /// Whether trying the same thing again could plausibly work.
    ///
    /// The UI uses this to decide whether to offer a retry at all - offering
    /// one for a banned account is worse than offering nothing.
    pub fn is_retryable(&self) -> bool {
        matches!(
            self,
            AshError::Transport { .. }
                | AshError::UnexpectedStatus { .. }
                | AshError::SignInExpired
                | AshError::SignInDeclined
                | AshError::SignInFailed { .. }
                | AshError::SessionExpired
                | AshError::VerificationFailed { .. }
                | AshError::Cancelled
                | AshError::LaunchFailed { .. }
                | AshError::FileVanished { .. }
                | AshError::OutOfSpace
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Error, ErrorKind};

    /// The OS code for a full volume, as this platform reports it.
    fn disk_full() -> Error {
        Error::from_raw_os_error(if cfg!(windows) { 112 } else { 28 })
    }

    #[test]
    fn a_full_disk_is_told_apart_from_every_other_write_failure() {
        let err = AshError::writing("writing a temp file")(disk_full());

        // "Check disk space and permissions" is a guess. This is the one
        // case where ash knows, and saying so is the difference between a
        // player fixing it and a player filing a bug.
        assert_eq!(err.kind(), "out_of_space");
        assert!(err.user_message().contains("disk is full"), "{}", err.user_message());
        assert!(err.is_retryable(), "freeing space and trying again is exactly the fix");
    }

    #[test]
    fn any_other_write_failure_stays_generic() {
        let err =
            AshError::writing("writing a temp file")(Error::from(ErrorKind::PermissionDenied));
        assert_eq!(err.kind(), "storage");
    }

    #[test]
    fn a_full_disk_on_the_other_platforms_code_is_not_claimed() {
        // 28 on Windows is ERROR_NOT_SAME_DEVICE, and 112 on Unix is not
        // ENOSPC. Matching both everywhere would mislabel real failures.
        let other = Error::from_raw_os_error(if cfg!(windows) { 28 } else { 112 });
        assert_eq!(AshError::writing("x")(other).kind(), "storage");
    }

    #[test]
    fn no_user_message_leaks_a_path_a_url_or_a_token() {
        let leaky = [
            AshError::Transport {
                url: "https://api.minecraftservices.com/x".into(),
                detail: "tls".into(),
            },
            AshError::UnexpectedStatus {
                url: "https://piston-meta.mojang.com".into(),
                status: 500,
            },
            AshError::Malformed { url: "https://x".into(), detail: "expected value".into() },
            AshError::Storage { detail: "C:/Users/someone/secret: denied".into() },
            AshError::LaunchFailed { detail: "C:/Users/someone/java.exe".into() },
            AshError::InstanceNotFound { id: "whatever".into() },
        ];

        for err in leaky {
            let message = err.user_message();
            assert!(!message.contains("http"), "{} leaks a url: {message}", err.kind());
            assert!(!message.contains("C:/"), "{} leaks a path: {message}", err.kind());
        }
    }

    #[test]
    fn the_files_a_player_must_be_able_to_name_are_named() {
        // The exception to the rule above, and a deliberate one: these are
        // paths inside ash's own depot, and they are what a player needs in
        // order to add an antivirus exclusion.
        let path = "libraries/com/example/thing.jar";
        assert!(AshError::VerificationFailed { path: path.into() }.user_message().contains(path));
        assert!(AshError::FileVanished { path: path.into() }.user_message().contains(path));
    }

    #[test]
    fn every_user_message_is_one_clean_line() {
        // Two of these messages spent a release with a run of twenty-two
        // spaces in the middle of the sentence, from a line continuation
        // that lost its backslash. Nothing failed, because nothing looked.
        let s = "x".to_string();
        let every = [
            AshError::Transport { url: s.clone(), detail: s.clone() },
            AshError::UnexpectedStatus { url: s.clone(), status: 500 },
            AshError::Malformed { url: s.clone(), detail: s.clone() },
            AshError::Storage { detail: s.clone() },
            AshError::OutOfSpace,
            AshError::Credential { detail: s.clone() },
            AshError::VerificationFailed { path: s.clone() },
            AshError::FileVanished { path: s.clone() },
            AshError::Cancelled,
            AshError::InstanceNotFound { id: s.clone() },
            AshError::InvalidInstanceName { detail: s.clone() },
            AshError::UnknownVersion { version_id: s.clone() },
            AshError::RuntimeUnavailable { component: s.clone(), platform: s.clone() },
            AshError::NoSignInPending,
            AshError::SignInExpired,
            AshError::SignInDeclined,
            AshError::SignInFailed { detail: s.clone() },
            AshError::SessionExpired,
            AshError::XboxNoAccount,
            AshError::XboxBanned,
            AshError::XboxRegionUnavailable,
            AshError::XboxAdultVerificationRequired,
            AshError::XboxChildAccount,
            AshError::XboxOther { code: 1 },
            AshError::NotAllowListed,
            AshError::NotEntitled,
            AshError::ProfileUnavailable,
            AshError::NoAccountSelected,
            AshError::AccountNotFound { profile_id: s.clone() },
            AshError::InvalidSetting { detail: s.clone() },
            AshError::LaunchUnsupported { version_id: s.clone(), detail: s.clone() },
            AshError::LaunchFailed { detail: s.clone() },
            AshError::AlreadyRunning { id: s.clone() },
        ];

        // The list has to be exhaustive to be worth anything, and the only
        // thing that can say so is `kind`, which has one arm per variant.
        let kinds: std::collections::BTreeSet<_> = every.iter().map(|e| e.kind()).collect();
        assert_eq!(kinds.len(), every.len(), "a variant is listed twice, or one is missing");

        for err in &every {
            let m = err.user_message();
            assert!(!m.is_empty(), "{} has no message", err.kind());
            assert!(!m.contains("  "), "{} has a run of spaces: {m:?}", err.kind());
            assert!(!m.contains('\n'), "{} spans lines: {m:?}", err.kind());
            assert!(!m.contains('\t'), "{} has a tab: {m:?}", err.kind());
            assert!(m.ends_with('.'), "{} does not end a sentence: {m:?}", err.kind());
        }
    }
}
