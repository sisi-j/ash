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
}

impl AshError {
    /// A stable machine-readable discriminant. Safe to match on in the UI;
    /// unlike the Display text, it is part of the contract.
    pub fn kind(&self) -> &'static str {
        match self {
            AshError::Transport { .. } => "transport",
            AshError::UnexpectedStatus { .. } => "unexpected_status",
            AshError::Malformed { .. } => "malformed",
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
        }
    }
}
