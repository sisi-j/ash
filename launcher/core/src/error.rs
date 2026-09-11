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
        )
    }
}
