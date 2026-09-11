use thiserror::Error;

/// Everything ash-core can fail with.
///
/// The UI never sees a raw error string. The adapter maps a variant to a
/// message via [`AshError::user_message`], and `kind` gives it something
/// stable to branch on that is not the display text.
#[derive(Debug, Error)]
pub enum AshError {
    #[error("transport failure talking to {url}: {detail}")]
    Transport { url: String, detail: String },

    #[error("{url} answered {status}")]
    UnexpectedStatus { url: String, status: u16 },

    #[error("could not parse the response from {url}: {detail}")]
    Malformed { url: String, detail: String },

    #[error("storage problem: {detail}")]
    Storage { detail: String },

    #[error("no instance with id {id}")]
    InstanceNotFound { id: String },

    #[error("invalid instance name: {detail}")]
    InvalidInstanceName { detail: String },
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
            AshError::InstanceNotFound { .. } => "instance_not_found",
            AshError::InvalidInstanceName { .. } => "invalid_instance_name",
        }
    }

    /// What a player should be told. Never leaks a URL, a token, or a
    /// library's error text.
    pub fn user_message(&self) -> String {
        match self {
            AshError::Transport { .. } => {
                "Could not reach Mojang. Check your connection and try again.".into()
            }
            AshError::UnexpectedStatus { .. } => {
                "Mojang's servers returned an unexpected response. Try again shortly.".into()
            }
            AshError::Malformed { .. } => {
                "Mojang's response could not be understood. This is likely a bug in ash.".into()
            }
            AshError::Storage { .. } => {
                "ash could not write to its own data folder. Check disk space and permissions.".into()
            }
            AshError::InstanceNotFound { .. } => {
                "That instance no longer exists. It may have been deleted outside ash.".into()
            }
            AshError::InvalidInstanceName { .. } => {
                "That name can't be used. Give the instance a name with at least one character.".into()
            }
        }
    }
}
