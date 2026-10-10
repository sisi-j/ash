//! Signing a player in to their ash account, on ash's backend (spec 0004,
//! ADR-0020).
//!
//! Severable by construction: nothing here returns an error to a caller that
//! is about to play. A sign-in that cannot complete leaves a status the
//! launcher shows quietly, and the game launches exactly as it would have.
//!
//! What the backend is sent: an empty request for a challenge, then the
//! challenge and the player's username. Never a Microsoft or Minecraft token -
//! the Minecraft token goes to Mojang's session server, in
//! [`crate::session_server`], and nowhere else.

use serde::{Deserialize, Serialize};

use crate::credentials::CredentialStore;
use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest, HttpResponse};

/// Renew an ash session this long before it ends, so a player is never
/// signed out of ash mid-session for want of a timely renewal.
pub(crate) const RENEW_WITHIN_MS: u64 = 7 * 24 * 60 * 60 * 1000;

/// Where a player stands with their ash account, for the launcher to show.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "state", rename_all = "snake_case")]
pub enum AshAccountStatus {
    /// Signed in, with a session that has not ended.
    SignedIn,
    /// No ash session on this machine, and no attempt has failed yet.
    NotSignedIn,
    /// The player deleted their ash account here. Nothing is signed in
    /// again until they sign in to Microsoft again.
    Deleted,
    /// ash's servers, or Mojang's session server, could not be reached or
    /// could not answer. Retried later; playing is unaffected.
    Unreachable,
    /// The Microsoft account cannot use online features: multiplayer is
    /// disabled or banned. `kind` is the error's kind, for its message.
    Refused { kind: String, message: String },
}

/// The ash session kept in the credential store, beside the refresh token.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct StoredSession {
    pub token: String,
    pub expires_at: u64,
}

pub(crate) fn session_key(profile_id: &str) -> String {
    format!("ash-session:{profile_id}")
}

pub(crate) fn stored_session(
    credentials: &dyn CredentialStore,
    profile_id: &str,
) -> Option<StoredSession> {
    let raw = credentials.get(&session_key(profile_id)).ok()??;
    serde_json::from_str(&raw).ok()
}

pub(crate) fn store_session(
    credentials: &dyn CredentialStore,
    profile_id: &str,
    session: &StoredSession,
) -> Result<(), AshError> {
    let encoded = serde_json::to_string(session)
        .map_err(|e| AshError::Storage { detail: format!("encoding the ash session: {e}") })?;
    credentials.set(&session_key(profile_id), &encoded)
}

/// `DELETE /v1/account` with the session's token: the account and everything
/// about it, gone (sisi-j/ash-backend#4).
pub(crate) async fn delete(http: &dyn HttpPort, base: &str, token: &str) -> Result<(), AshError> {
    let url = format!("{base}/v1/account");
    let response = http.send(HttpRequest::delete(&url).bearer(token)).await?;
    if response.is_success() {
        Ok(())
    } else {
        Err(AshError::UnexpectedStatus { url, status: response.status })
    }
}

// ---- the backend's answers (sisi-j/ash-backend, contract/) -----------------

#[derive(Deserialize)]
struct Challenge {
    challenge: String,
}

#[derive(Deserialize)]
pub(crate) struct SignedIn {
    pub session: SessionBody,
    pub account: AccountBody,
}

#[derive(Deserialize)]
pub(crate) struct SessionBody {
    pub token: String,
    pub expires_at: u64,
}

#[derive(Deserialize)]
pub(crate) struct AccountBody {
    pub created: bool,
}

pub(crate) async fn challenge(http: &dyn HttpPort, base: &str) -> Result<String, AshError> {
    let url = format!("{base}/v1/session/challenge");
    let response = http.send(HttpRequest::post_json(&url, &serde_json::json!({}))?).await?;
    let body: Challenge = decode(&url, &response)?;
    Ok(body.challenge)
}

pub(crate) async fn redeem(
    http: &dyn HttpPort,
    base: &str,
    challenge: &str,
    username: &str,
) -> Result<SignedIn, AshError> {
    let url = format!("{base}/v1/session");
    let body = serde_json::json!({ "challenge": challenge, "username": username });
    let response = http.send(HttpRequest::post_json(&url, &body)?).await?;
    decode(&url, &response)
}

fn decode<T: serde::de::DeserializeOwned>(
    url: &str,
    response: &HttpResponse,
) -> Result<T, AshError> {
    // Every failure the backend names (contract/README.md) means the same to
    // the player: ash couldn't sign them in this time, and will try again.
    if !response.is_success() {
        return Err(AshError::UnexpectedStatus { url: url.to_owned(), status: response.status });
    }
    serde_json::from_slice(&response.body)
        .map_err(|e| AshError::Malformed { url: url.to_owned(), detail: e.to_string() })
}
