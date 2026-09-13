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

    #[error("credential store problem: {detail}")]
    Credential { detail: String },

    #[error("{path} did not match its published hash")]
    VerificationFailed { path: String },

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

impl AshError {
    /// A stable machine-readable discriminant. Safe to match on in the UI;
    /// unlike the Display text, it is part of the contract.
    pub fn kind(&self) -> &'static str {
        match self {
            AshError::Transport { .. } => "transport",
            AshError::UnexpectedStatus { .. } => "unexpected_status",
            AshError::Malformed { .. } => "malformed",
            AshError::Storage { .. } => "storage",
            AshError::Credential { .. } => "credential",
            AshError::VerificationFailed { .. } => "verification_failed",
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
            AshError::Credential { .. } => {
                "ash could not use the Windows credential store. Your sign-in was not saved.".into()
            }
            AshError::VerificationFailed { .. } => {
                "A downloaded file kept arriving corrupted. Check your connection, and any antivirus or proxy that might be altering downloads."
                    .into()
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
                "Mojang doesn't publish a Java runtime this version can use on your platform.".into()
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
            AshError::XboxBanned => {
                "This Xbox account is banned and can't be used to play.".into()
            }
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
            AshError::NotEntitled => {
                "This account doesn't own Minecraft: Java Edition.".into()
            }
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
            AshError::AccountNotFound { .. } => {
                "That account is no longer on this machine.".into()
            }
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
        )
    }
}
